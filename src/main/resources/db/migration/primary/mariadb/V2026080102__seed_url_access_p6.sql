-- ============================================================================
-- P6 접근 규칙 시드 — 부가 도메인 사용자 화면 (설문·민원·일정·휴일)
-- ============================================================================
-- 관리자 화면은 P2 의 `/admin/system/**`·`/admin/**` 포괄 규칙이 이미 받는다.
-- 여기서는 **사용자 측 URL** 만 연다.
--
-- ⚠️ 이 네 도메인은 URL 최상위 세그먼트를 차지한다(`/survey`·`/complaint`·
--    `/schedule`·`/holiday`). `DefaultUsrController` 의 `/{siteCode}/{slug}` 패턴과
--    형태가 겹치지만, Spring 은 **리터럴 매핑을 패턴보다 우선**하므로 가로채이지 않는다.
--    같은 이름의 site_code 를 만들면 그 사이트가 열리지 않는다는 뜻이기도 하다 —
--    사이트 코드에 이 네 단어를 쓰지 말 것.
--
-- 재실행 멱등 — 이 파일이 관리하는 패턴만 지우고 다시 넣는다.
-- ============================================================================

SET @actor := 'SYSTEM';

DELETE FROM `tb_role_url_access`
 WHERE `url_pattern` IN (
   '/survey/**',
   '/complaint/**',
   '/schedule/**',
   '/holiday/**'
 );

INSERT INTO `tb_role_url_access`
  (url_access_id, url_pattern, http_method, access_type, allowed_user_types, required_roles,
   require_csrf_yn, require_2fa_yn, priority, `DESCRIPTION`, use_yn, delete_yn, created_by, created_at)
VALUES

  -- ── 민원 — 쓰기는 인증 필수 ─────────────────────────────────────────────
  -- 민원은 작성자를 특정할 수 있어야 한다(답변 통지·본인 확인). 익명 접수를 열면
  -- 답변을 어디로 보낼지도, 본인만 볼 수 있게 할 근거도 없어진다.
  -- 상세 조회의 본인 확인은 컨트롤러가 한다 — URL 계층은 거친 문만 담당한다.

  ('00000000-0000-7000-8001-000000000301', '/complaint/**', 'POST', 'AUTHENTICATED', NULL, NULL,
   'Y', 'N', 130, '민원 작성·수정·삭제·종결 — 작성자를 특정할 수 있어야 한다', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000302', '/complaint/**', 'GET', 'AUTHENTICATED', NULL, NULL,
   'Y', 'N', 230, '민원 목록·상세 — 본인 민원 여부 판정은 컨트롤러가 한다', 'Y', 'N', @actor, current_timestamp()),

  -- ── 설문 — 익명 응답을 지원한다 ─────────────────────────────────────────
  -- SurveyResponseServiceImpl 에 `anonymous` 분기가 있다. 익명 설문은 memberId 가
  -- null 이라 중복 차단이 성립하지 않는데, 그건 **설문 설계자의 선택**이지
  -- URL 계층이 막을 일이 아니다. POST 를 인증으로 잠그면 익명 설문 자체가 죽는다.

  ('00000000-0000-7000-8001-000000000303', '/survey/**', 'ALL', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 240, '설문 참여·제출 — 익명 응답 지원(중복 차단은 회원 응답에만 성립)', 'Y', 'N', @actor, current_timestamp()),

  -- ── 일정·휴일 — 공개 조회 전용 ──────────────────────────────────────────
  -- 사용자 측에는 달력·주간·상세 조회만 있다. 등록·수정은 관리자 화면이라
  -- `/admin/**` 규칙이 받는다. GET 만 여는 이유는 그것 말고 열 것이 없기 때문이다.

  ('00000000-0000-7000-8001-000000000304', '/schedule/**', 'GET', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 240, '일정 달력·주간·상세 공개 조회', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000305', '/holiday/**', 'GET', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 240, '휴일 달력·상세 공개 조회', 'Y', 'N', @actor, current_timestamp());
