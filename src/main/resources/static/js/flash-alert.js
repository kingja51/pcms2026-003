(function () {
  'use strict';

  /**
   * flash-alert.js — 서버 flash 메시지를 alert 대화상자로 1회 표시.
   *
   * 엄격 CSP 환경에서 인라인 <script>alert(...)</script> 사용이 금지되므로
   * 마크업에 data-flash-alert="메시지" 속성으로 선언하면 이 스크립트가 대신 처리.
   * htmx 부분 갱신에도 대응 — htmx:load 에서 멱등 초기화(data-initialized 가드).
   */
  function init(root) {
    var scope = (root && root.querySelectorAll) ? root : document;
    var nodes = scope.querySelectorAll('[data-flash-alert]');
    Array.prototype.forEach.call(nodes, function (el) {
      if (el.getAttribute('data-initialized') === 'true') return;
      el.setAttribute('data-initialized', 'true');
      var msg = el.getAttribute('data-flash-alert');
      if (msg) window.alert(msg);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { init(document); });
  } else {
    init(document);
  }
  document.addEventListener('htmx:load', function (ev) { init(ev.target); });
})();
