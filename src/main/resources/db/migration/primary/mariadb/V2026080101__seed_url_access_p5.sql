-- ============================================================================
-- P5 접근 규칙 시드 — 회원 화면 (/member/**)
-- ============================================================================
-- memberFilterChain(Order 20) 의 `.requestMatchers(...).permitAll()` 에 걸리는 경로는
-- DynamicAuthorizationManager 까지 오지 않는다(체인에서 먼저 통과). 그 외 경로는
-- 전부 DB 규칙이 판정하고 **무매칭이면 DENY** 다.
--
-- 체인 permitAll 목록(SecurityConfig.memberFilterChain):
--   /member/login · /member/join · /member/join/** · /member/find/**
--   /member/dormant/** · /member/oauth2/** · /member/identity/nice(/**)
--
-- ⚠️ **주의 — 체인 패턴과 실제 URL 이 어긋난 곳이 있다.**
--    체인은 `/member/find/**` 인데 컨트롤러는 `/member/find-id`·`/member/find-password`
--    를 매핑한다(하이픈, 슬래시 아님). 즉 **체인 permitAll 에 걸리지 않는다.**
--    001 에서 그대로 넘어온 불일치다. 여기서 DB 규칙으로 확실히 열어 둔다 —
--    체인 패턴을 고치는 편이 근본적이지만 그건 P2 산출물 수정이라 별건으로 둔다
--    (PLAN §7 기록).
--
-- 재실행 멱등 — 이 파일이 관리하는 패턴만 지우고 다시 넣는다.
-- ============================================================================

SET @actor := 'SYSTEM';

DELETE FROM `tb_role_url_access`
 WHERE `url_pattern` IN (
   '/member/find-id', '/member/find-password',
   '/member/logout',
   '/member/dashboard',
   '/member/mypage', '/member/mypage/**',
   '/member/**'
 );

INSERT INTO `tb_role_url_access`
  (url_access_id, url_pattern, http_method, access_type, allowed_user_types, required_roles,
   require_csrf_yn, require_2fa_yn, priority, `DESCRIPTION`, use_yn, delete_yn, created_by, created_at)
VALUES

  -- ── 익명 진입점 ─────────────────────────────────────────────────────────
  -- 아이디·비밀번호 찾기는 로그인 전에 쓰는 화면이라 인증을 요구할 수 없다.
  -- 계정 열거 방어는 화면·서비스 쪽 책임이다(동일 응답·동일 소요시간).

  ('00000000-0000-7000-8001-000000000201', '/member/find-id', 'ALL', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 100, '아이디 찾기 — 체인 permitAll(/member/find/**) 패턴에 안 걸려 DB 로 연다', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000202', '/member/find-password', 'ALL', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 100, '비밀번호 찾기 — 위와 동일', 'Y', 'N', @actor, current_timestamp()),

  -- ── 로그아웃 ────────────────────────────────────────────────────────────
  -- 인증 상태에서만 의미가 있다. POST 전용은 체인의 logoutRequestMatcher 가 강제한다.

  ('00000000-0000-7000-8001-000000000203', '/member/logout', 'ALL', 'AUTHENTICATED', 'MEMBER', NULL,
   'Y', 'N', 110, '회원 로그아웃', 'Y', 'N', @actor, current_timestamp()),

  -- ── 로그인 필요 ─────────────────────────────────────────────────────────
  -- 회원 인가는 tb_member_role 을 쓰지 않는다 — AUTHENTICATED + user_type=MEMBER 다
  -- (CLAUDE.md 보안 규약). 역할을 요구하면 v_user_login 의 상수 role_codes 와 얽힌다.

  ('00000000-0000-7000-8001-000000000204', '/member/dashboard', 'ALL', 'AUTHENTICATED', 'MEMBER', NULL,
   'Y', 'N', 120, '회원 대시보드', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000205', '/member/mypage', 'ALL', 'AUTHENTICATED', 'MEMBER', NULL,
   'Y', 'N', 120, '마이페이지 홈', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000206', '/member/mypage/**', 'ALL', 'AUTHENTICATED', 'MEMBER', NULL,
   'Y', 'N', 120, '마이페이지 — 프로필·비밀번호·재인증·탈퇴. 본인 데이터만 다룬다', 'Y', 'N', @actor, current_timestamp()),

  -- ── 포괄 규칙 ───────────────────────────────────────────────────────────
  -- 위에서 안 잡힌 /member/** 는 인증을 요구한다. 무매칭 DENY 로 두지 않는 이유:
  -- 회원 화면이 늘 때마다 규칙을 빠뜨리면 **조용히 안 열리는** 화면이 생기는데,
  -- 그 실패는 404 도 500 도 아니라 로그인 페이지로의 리다이렉트라 원인 추적이 어렵다.
  -- 포괄 규칙을 두면 최소한 "로그인하면 열린다" 는 예측 가능한 상태가 된다.
  -- 더 조여야 하는 경로는 위쪽에 낮은 priority 로 개별 규칙을 추가한다.

  ('00000000-0000-7000-8001-000000000207', '/member/**', 'ALL', 'AUTHENTICATED', 'MEMBER', NULL,
   'Y', 'N', 9200, '회원 영역 포괄 — 개별 규칙에 안 걸린 나머지', 'Y', 'N', @actor, current_timestamp());
