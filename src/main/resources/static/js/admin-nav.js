(function () {
  'use strict';

  /**
   * 관리자 헤더 내비 드롭다운(.admin-nav-group = <details>) 동작.
   *   1) 한 그룹을 열면 나머지 그룹은 닫는다.
   *   2) 그룹 외부 클릭 / Escape 키로 모두 닫는다.
   *
   * 엄격 CSP(인라인 스크립트 금지) + htmx 부분 렌더 환경이라
   * 요소별 리스너 대신 document 위임 1회 등록으로 처리 — 재초기화 불필요.
   */
  function closeAll(except) {
    document.querySelectorAll('.admin-nav-group[open]').forEach(function (g) {
      if (g !== except) g.open = false;
    });
  }

  document.addEventListener('click', function (ev) {
    var summary = ev.target.closest('.admin-nav-group > summary');
    if (summary) {
      // 기본 토글 동작은 그대로 두고, 다른 그룹만 닫는다.
      closeAll(summary.parentElement);
      return;
    }
    if (!ev.target.closest('.admin-nav-group')) closeAll(null);
  });

  document.addEventListener('keydown', function (ev) {
    if (ev.key === 'Escape') closeAll(null);
  });
})();
