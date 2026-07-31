(function () {
  'use strict';

  /**
   * form[data-confirm] 에 대해 submit 시 confirm 대화상자를 띄운다.
   * 엄격 CSP 환경에서 inline onsubmit="..." 사용이 금지되므로
   * data-confirm="메시지" 속성으로 선언하면 이 핸들러가 대신 처리.
   */
  function wireConfirmForms() {
    document.addEventListener('submit', function (ev) {
      var form = ev.target;
      if (!(form instanceof HTMLFormElement)) return;
      var msg = form.dataset.confirm;
      if (!msg) return;
      if (!window.confirm(msg)) {
        ev.preventDefault();
        ev.stopPropagation();
      }
    }, true);
  }

  /**
   * 공용 picker 패턴 — 직원(부서장)/역할/기타 선택 UI.
   *
   * 마크업 계약:
   *   form[data-manager-picker]
   *     input[data-manager-picker-value]   ← hidden input (실제 저장값)
   *     [data-manager-picker-label]        ← 사용자에게 표시될 현재 선택 라벨 (textContent 갱신)
   *     button[data-manager-picker-clear]  ← 선택 해제 버튼 (value="", label="(미지정)")
   *     [hx-target=#mgrPickList] 안의 버튼[data-pick-employee-id][data-pick-employee-label]
   *                                       ← htmx 로 로드된 검색 결과 버튼. 클릭 시 값·라벨 갱신
   *
   * CSP nonce 환경이라 inline onclick 사용 불가 → 위임 이벤트 처리.
   */
  function wireManagerPicker() {
    document.addEventListener('click', function (ev) {
      // 1) 결과 항목 클릭 → 현재 선택값 갱신
      var pick = ev.target.closest('[data-pick-employee-id]');
      if (pick) {
        var form = pick.closest('form[data-manager-picker]');
        if (form) {
          applyManagerPick(form,
            pick.getAttribute('data-pick-employee-id') || '',
            pick.getAttribute('data-pick-employee-label') || '');
          ev.preventDefault();
        }
        return;
      }

      // 2) 선택 해제 버튼 → 값 비우기
      var clr = ev.target.closest('[data-manager-picker-clear]');
      if (clr) {
        var form2 = clr.closest('form[data-manager-picker]');
        if (form2) {
          applyManagerPick(form2, '', '(미지정)');
          ev.preventDefault();
        }
      }
    });
  }

  function applyManagerPick(form, value, label) {
    var hidden = form.querySelector('[data-manager-picker-value]');
    var labelEl = form.querySelector('[data-manager-picker-label]');
    if (hidden) hidden.value = value;
    if (labelEl) labelEl.textContent = label;
  }

  /**
   * [data-history-back] 속성이 붙은 버튼 클릭 시 브라우저 history.back() 호출.
   * 상세보기에서 "뒤로가기" 를 눌러 직전 목록 화면(검색조건 포함)으로 복귀.
   * history.length <= 1 이면 fallback URL(data-history-back-fallback) 로 이동.
   * 엄격 CSP 로 inline onclick 이 금지되어 위임 핸들러로 처리.
   */
  function wireHistoryBack() {
    document.addEventListener('click', function (ev) {
      var btn = ev.target.closest('[data-history-back]');
      if (!btn) return;
      ev.preventDefault();
      var fallback = btn.getAttribute('data-history-back-fallback');
      if (window.history.length > 1) {
        window.history.back();
      } else if (fallback) {
        window.location.href = fallback;
      }
    });
  }

  /**
   * HTMX 요청에 CSRF 헤더 자동 주입.
   *
   * Spring Security 6 의 CookieCsrfTokenRepository.withHttpOnlyFalse() 사용.
   * - 토큰 값: <meta name="_csrf">
   * - 헤더 이름: <meta name="_csrf_header"> (기본 X-XSRF-TOKEN)
   *
   * HTMX 는 form[method=post] 가 아닌 hx-post 요청을 보내므로 Spring 의 Thymeleaf
   * 자동 _csrf hidden input 주입이 동작하지 않는다. 본 인터셉터가 헤더 방식으로 보강.
   */
  function wireHtmxCsrf() {
    var token  = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (!token || !header) return;
    var tokenValue = token.getAttribute('content');
    var headerName = header.getAttribute('content');
    if (!tokenValue || !headerName) return;
    document.body.addEventListener('htmx:configRequest', function (ev) {
      if (!ev.detail || !ev.detail.headers) return;
      ev.detail.headers[headerName] = tokenValue;
    });
  }

  /**
   * 이미지 로드 실패 폴백 — 인라인 onerror 금지(CSP) 대체. error 는 버블링하지
   * 않으므로 캡처 단계 위임 1회 등록으로 처리 (htmx 재렌더에도 재초기화 불필요).
   *
   * 마크업 계약 (img 속성):
   *   data-src-fallback="maxresdefault:hqdefault"
   *       → src 의 앞 토큰을 뒤 토큰으로 1회 치환해 재시도 (유튜브 썸네일 등)
   *   data-hide-on-error=".bd-thumb"
   *       → 실패 시 closest(셀렉터) 조상에 hidden 부여 (값이 빈 문자열이면 자신)
   *   data-show-next-on-error
   *       → 실패 시 자신을 hidden 하고 다음 형제의 hidden 제거 (플레이스홀더 노출)
   */
  function wireImgFallback() {
    document.addEventListener('error', function (ev) {
      var img = ev.target;
      if (!(img instanceof HTMLImageElement)) return;

      var swap = img.getAttribute('data-src-fallback');
      if (swap) {
        img.removeAttribute('data-src-fallback'); // 무한 재시도 방지 — 1회만
        var parts = swap.split(':');
        if (parts.length === 2 && img.src.indexOf(parts[0]) !== -1) {
          img.src = img.src.split(parts[0]).join(parts[1]);
          return;
        }
      }

      if (img.hasAttribute('data-show-next-on-error')) {
        img.classList.add('hidden');
        if (img.nextElementSibling) img.nextElementSibling.classList.remove('hidden');
        return;
      }

      var sel = img.getAttribute('data-hide-on-error');
      if (sel !== null) {
        var target = sel ? img.closest(sel) : img;
        if (target) target.classList.add('hidden');
      }
    }, true);
  }

  /**
   * 고대비(High contrast) 모드 토글 — [data-action="toggle-contrast"] 클릭 시
   * <html> 의 .hc 클래스를 켜고/끄고 localStorage('pcms-hc')에 유지한다.
   * 초기 복원은 <head> 의 nonce 인라인 부트스트랩이 FOUC 없이 처리하므로
   * 여기서는 토글과 버튼 상태(aria-pressed) 동기화만 담당. 이벤트 위임 1회 등록.
   */
  var HC_KEY = 'pcms-hc';

  function syncContrastButtons() {
    var on = document.documentElement.classList.contains('hc');
    var btns = document.querySelectorAll('[data-action="toggle-contrast"]');
    for (var i = 0; i < btns.length; i++) {
      btns[i].setAttribute('aria-pressed', on ? 'true' : 'false');
    }
  }

  function wireContrastToggle() {
    document.addEventListener('click', function (ev) {
      var btn = ev.target.closest('[data-action="toggle-contrast"]');
      if (!btn) return;
      ev.preventDefault();
      var on = document.documentElement.classList.toggle('hc');
      try { localStorage.setItem(HC_KEY, on ? '1' : '0'); } catch (e) {}
      syncContrastButtons();
    });
    // htmx 부분 교체 후에도 버튼 상태 재동기화 (멱등).
    document.body.addEventListener('htmx:load', syncContrastButtons);
    syncContrastButtons();
  }

  /**
   * [role="button"] 키보드 활성화 — Enter/Space 로 click 이벤트를 발생시킨다.
   * htmx 의 hx-trigger 이벤트 필터(keyup[key=='Enter'])는 내부에서 Function()(eval)을
   * 생성해 엄격 CSP(script-src nonce, unsafe-eval 없음)에 걸리므로, 트리거는 click 만
   * 선언하고 키보드 접근성은 이 위임 핸들러가 담당한다. 네이티브 인터랙티브 요소는
   * 자체 동작이 있으므로 제외. 이벤트 위임 1회 등록.
   */
  function wireRoleButtonKeys() {
    document.addEventListener('keydown', function (ev) {
      if (ev.key !== 'Enter' && ev.key !== ' ') return;
      var el = ev.target.closest('[role="button"]');
      if (!el) return;
      // button/a[href]/input 등 네이티브 요소는 브라우저 기본 동작에 맡긴다.
      if (ev.target.closest('button, a[href], input, select, textarea')) return;
      ev.preventDefault(); // Space 스크롤 방지
      el.click();
    });
  }

  /**
   * 현재 페이지 인쇄 — [data-action="print"] 클릭 시 window.print().
   * breadcrumb 밴드 우측 유틸 버튼(레이아웃 공통)이 사용. 이벤트 위임 1회 등록.
   */
  function wirePrintButton() {
    document.addEventListener('click', function (ev) {
      var btn = ev.target.closest('[data-action="print"]');
      if (!btn) return;
      ev.preventDefault();
      window.print();
    });
  }

  /**
   * 네이티브 <dialog> 열기/닫기 — 엄격 CSP 라 inline onclick 불가, 위임 1회 등록.
   *
   * 마크업 계약:
   *   [data-action="dialog-open"][data-dialog-target="#id"]  ← showModal() 호출
   *   dialog 내부 [data-action="dialog-close"]               ← 가장 가까운 dialog close()
   *   백드롭(패딩 밖) 클릭·ESC 는 브라우저 기본 동작에 위임 — 클릭 target 이
   *   dialog 자신이면 내용 밖(백드롭)이므로 닫는다 (내용 컨테이너가 전체를 덮는 전제).
   */
  function wireDialogButtons() {
    document.addEventListener('click', function (ev) {
      var open = ev.target.closest('[data-action="dialog-open"]');
      if (open) {
        ev.preventDefault();
        var sel = open.getAttribute('data-dialog-target');
        var dlg = sel ? document.querySelector(sel) : null;
        if (dlg && typeof dlg.showModal === 'function' && !dlg.open) dlg.showModal();
        return;
      }
      var close = ev.target.closest('[data-action="dialog-close"]');
      if (close) {
        ev.preventDefault();
        var d = close.closest('dialog');
        if (d) d.close();
        return;
      }
      if (ev.target instanceof HTMLDialogElement && ev.target.open) {
        ev.target.close();
      }
    });
  }

  function init() {
    wireConfirmForms();
    wireManagerPicker();
    wireHistoryBack();
    wireHtmxCsrf();
    wireImgFallback();
    wireContrastToggle();
    wireRoleButtonKeys();
    wirePrintButton();
    wireDialogButtons();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
