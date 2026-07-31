/*
 * notification-bell.js — 헤더 종 아이콘의 미확인 카운트 폴링.
 *
 * 사용 — fragments/notification-bell.html 가 렌더된 페이지에서 자동 동작.
 * 비인증 사용자에게는 종 아이콘 자체가 렌더되지 않으므로 본 스크립트도 노옵.
 *
 * 동작
 *   1) DOMContentLoaded 즉시 1회 fetch
 *   2) 30초 간격 setInterval 폴링
 *   3) 탭 비가시 상태(visibilityState='hidden')면 폴링 정지, 다시 visible 되면 즉시 1회 fetch + 재개
 *   4) /api/v1/notification/unread-count 응답 {ok:true, data:{unread:N}} 의 N 을 배지에 반영
 *      · N=0 → 배지 hidden
 *      · 0<N<100 → "N"
 *      · N>=100 → "99+"
 *   5) 401/403 등 인증 만료 시 콘솔 디버그만, 화면 영향 없음
 *
 * 폴링 간격 변경 — data-noti-interval-ms 속성으로 override 가능 (#notiBell 요소).
 */
(function () {
  'use strict';

  const DEFAULT_INTERVAL_MS = 30000;
  const ENDPOINT            = '/api/v1/notification/unread-count';

  function $(id) { return document.getElementById(id); }

  function applyCount(badge, n) {
    if (!badge) return;
    if (typeof n !== 'number' || n <= 0) {
      badge.classList.add('hidden');
      badge.textContent = '0';
      return;
    }
    badge.classList.remove('hidden');
    badge.textContent = n >= 100 ? '99+' : String(n);
  }

  async function fetchOnce(badge) {
    try {
      const res = await fetch(ENDPOINT, {
        credentials: 'same-origin',
        headers: { 'Accept': 'application/json' }
      });
      if (!res.ok) {
        // 401/403 등 — 인증 만료 가능. 배지 숨김 유지.
        if (res.status === 401 || res.status === 403) applyCount(badge, 0);
        return;
      }
      const json = await res.json();
      const unread = (json && json.data && typeof json.data.unread === 'number')
        ? json.data.unread : 0;
      applyCount(badge, unread);
    } catch (err) {
      // 네트워크 오류는 silent — 다음 주기에 재시도
      console.debug('[notification-bell] fetch failed:', err);
    }
  }

  function init() {
    const bell  = $('notiBell');
    const badge = $('notiBellBadge');
    if (!bell || !badge) return; // 비인증 페이지 — 종 아이콘 미렌더

    const intervalMs = parseInt(bell.dataset.notiIntervalMs, 10) || DEFAULT_INTERVAL_MS;

    let timer = null;
    function start() {
      if (timer) return;
      timer = setInterval(() => fetchOnce(badge), intervalMs);
    }
    function stop() {
      if (!timer) return;
      clearInterval(timer);
      timer = null;
    }

    // 탭 전환 시 폴링 일시정지 — 백그라운드 트래픽 절감
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        fetchOnce(badge);
        start();
      } else {
        stop();
      }
    });

    // 첫 진입 즉시 1회 + 주기 시작
    fetchOnce(badge);
    if (document.visibilityState === 'visible') start();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
