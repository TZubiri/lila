package lila.security

import play.api.i18n.Lang
import scala.concurrent.duration._
import scalatags.Text.all._

import lila.common.config._
import lila.common.EmailAddress
import lila.i18n.I18nKeys.{ emails => trans }
import lila.user.{ User, UserRepo }

final class Reopen(
    mailgun: Mailgun,
    userRepo: UserRepo,
    baseUrl: BaseUrl,
    tokenerSecret: Secret
)(implicit ec: scala.concurrent.ExecutionContext) {

  import Mailgun.html._

  def prepare(
      username: String,
      email: EmailAddress,
      hasModClose: User.ID => Fu[Boolean]
  ): Fu[Either[(String, String), User]] =
    userRepo.enabledWithEmail(email.normalize) flatMap {
      case Some(_) =>
        fuccess(Left("emailUsed" -> "This email address is already in use by an active account."))
      case _ =>
        val userId = User normalize username
        userRepo.byId(userId) flatMap {
          case None =>
            fuccess(Left("noUser" -> "No account has ever been created with this username."))
          case Some(user) if user.enabled =>
            fuccess(Left("alreadyActive" -> "This account is already active."))
          case Some(user) =>
            userRepo.prevEmail(user.id) flatMap {
              case None =>
                fuccess(
                  Left("noEmail" -> "That account doesn't have any associated email, and cannot be reopened.")
                )
              case Some(prevEmail) if !email.similarTo(prevEmail) =>
                fuccess(Left("differentEmail" -> "That account has a different email address."))
              case _ =>
                hasModClose(user.id) map {
                  case true => Left("nope" -> "Sorry, that account can no longer be reopened.")
                  case _    => Right(user)
                }
            }
        }
    }

  def send(user: User, email: EmailAddress)(implicit lang: Lang): Funit =
    tokener make user.id flatMap { token =>
      lila.mon.email.send.reopen.increment()
      val url = s"$baseUrl/account/reopen/login/$token"
      mailgun send Mailgun.Message(
        to = email,
        subject = s"Reopen your lichess.org account: ${user.username}",
        text = s"""
${trans.passwordReset_clickOrIgnore.literalTxtTo(lang)}

$url

${trans.common_orPaste.literalTxtTo(lang)}

${Mailgun.txt.serviceNote}
""",
        htmlBody = emailMessage(
          p(trans.passwordReset_clickOrIgnore.literalTo(lang)),
          potentialAction(metaName("Log in"), Mailgun.html.url(url)),
          serviceNote
        ).some
      )
    }

  def confirm(token: String): Fu[Option[User]] =
    tokener read token flatMap { _ ?? userRepo.disabledById } flatMap {
      _ ?? { user =>
        userRepo reopen user.id inject user.some
      }
    }

  private val tokener = LoginToken.makeTokener(tokenerSecret, 20 minutes)
}
