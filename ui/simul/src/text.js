var m = require('mithril');
var simul = require('./simul');
var xhr = require('./xhr');

function enrichText(text) {
  return m.trust(autolink(lichess.escapeHtml(text), toLink).replace(newLineRegex, '<br>'));
}
function autolink(str, callback) {
  return str.replace(linkRegex, (_, space, url) => space + callback(url));
}
function toLink(url) {
  if (commentYoutubeRegex.test(url)) return toYouTubeEmbed(url) || url;
  const show = imageTag(url) || url.replace(/https?:\/\//, '');
  return '<a target="_blank" rel="nofollow" href="' + url + '">' + show + '</a>';
}
// from ui/analyse
const linkRegex = /(^|[\s\n]|<[A-Za-z]*\/?>)((?:https?|ftp):\/\/[\-A-Z0-9+\u0026\u2019@#\/%?=()~_|!:,.;]*[\-A-Z0-9+\u0026@#\/%=~()_|])/gi;
const newLineRegex = /\n/g;

function editor(ctrl) {
  return m('div.editor', [
    m('button.button.button-empty.open', {
      onclick: ctrl.text.toggle
    }, 'Edit'),
    ctrl.text.editing () ? m('form', {
      onsubmit: function(e) {
        xhr.setText(ctrl, e.target.querySelector('textarea').value);
        ctrl.text.toggle();
        return false;
      }
    }, [
      m('textarea', ctrl.data.text),
      m('button.button.save', {
        type: 'submit'
      }, 'Save')
    ]) : null
  ]);
}

module.exports = {
  ctrl: function() {
    var editing = false;
    return {
      toggle: function() {
        editing = !editing;
      },
      editing: function() {
        return editing;
      }
    };
  },
  view: function(ctrl) {
    return ctrl.data.text || simul.createdByMe(ctrl) ? m('div.simul-text', {
      class: { editing: ctrl.text.editing() }
    }, [
      m('p', enrichText(ctrl.data.text)),
      simul.createdByMe(ctrl) ? editor(ctrl) : null
    ]) : null;
  }
}