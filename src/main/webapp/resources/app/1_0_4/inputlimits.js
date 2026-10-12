/*
 * Числовые поля не должны принимать значения вне диапазона своего типа.
 * PrimeFaces инициализирует AutoNumeric в режиме overrideMinMaxLimits="invalid":
 * значение за пределами minValue/maxValue вводится и лишь помечается неверным.
 * Переключаем на "ceiling" — тогда лишние разряды просто не набираются.
 */
(function () {
  function enforce() {
    if (!window.PrimeFaces || !PrimeFaces.widgets) {
      return;
    }
    Object.keys(PrimeFaces.widgets).forEach(function (name) {
      var widget = PrimeFaces.widgets[name];
      if (!widget || !widget.autonumeric) {
        return;
      }
      try {
        widget.autonumeric.update({ overrideMinMaxLimits: 'ceiling' });
      } catch (outOfRange) {
        // Текущее значение уже вне диапазона: ограничение вступит в силу,
        // когда пользователь исправит значение. Сообщение покажет сервер.
      }
    });
  }

  function enforceSoon() {
    window.setTimeout(enforce, 0);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', enforceSoon);
  } else {
    enforceSoon();
  }

  if (window.jQuery) {
    jQuery(document).on('pfAjaxComplete', enforceSoon);
  }
})();
