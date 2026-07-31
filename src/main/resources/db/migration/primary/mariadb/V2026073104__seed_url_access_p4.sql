-- ============================================================================
-- P4 접근 규칙 시드 — 사용자 화면(게시판·파일·알림·API·프로그램 쉘)
-- ============================================================================
-- DynamicAuthorizationManager 는 priority ASC 로 평가하고 **처음 매칭된 규칙**이
-- 판정한다. 무매칭이면 DENY 다. P2 시드(V2026073101)는 /admin·/csp-report 만 열어
-- 두었으므로, 이 파일이 없으면 P4 에서 만든 사용자 화면이 전부 403 이다.
--
-- ⚠️ 적용된 마이그레이션은 수정하지 않는다(체크섬 불일치로 기동 실패).
--    규칙을 바꾸려면 새 버전 파일을 추가한다.
--
-- ── 이 파일이 다루지 않는 것 ────────────────────────────────────────────────
--
--  ① 사이트별 규칙 `/{siteCode}`, `/{siteCode}/**`
--     DefaultUsrController 는 /{sc} 를 패턴으로 받지만, 접근 규칙은 **사이트마다
--     2행씩** 등록해야 한다(001 실측도 48개 학과 × 2행 = 96행). 여기에 catch-all
--     `/*` 를 넣으면 등록되지 않은 사이트 코드까지 열리므로 넣지 않는다.
--     tb_site 행이 아직 없어 지금 넣을 대상도 없다 — **사이트를 만들 때 함께 넣는다**.
--     관리자 사이트 등록 화면이 규칙까지 생성하게 하는 것이 최종 형태다(P8).
--
--  ② 회원 화면 `/member/**`
--     P5 범위다. 지금 열면 존재하지 않는 화면에 규칙만 남는다.
--
-- ── 인가 계층 설계 ──────────────────────────────────────────────────────────
--
--  URL 계층은 **거친 문(coarse gate)** 만 담당한다. 게시판의 readAuth/writeAuth/
--  downloadAuth, 비밀글 본인확인, 본인 글 수정·삭제 판정은 컨트롤러·서비스가 한다
--  (BoardUsrController.canWrite/canEdit/canDelete/canSeeSecret).
--  URL 규칙으로 세밀 권한을 흉내내면 두 곳이 어긋났을 때 어느 쪽이 진실인지 알 수 없다.
--
--  다만 **쓰기 경로는 URL 계층에서도 인증을 요구**한다. 컨트롤러가 이미 막지만
--  (canWrite 는 비로그인 시 false), 방어를 한 겹만 두지 않는다 — 웹쉘 침해 이력이 있다.
-- ============================================================================

SET @actor := 'SYSTEM';

-- 재실행 멱등 — 이 파일이 관리하는 패턴만 지우고 다시 넣는다.
DELETE FROM `tb_role_url_access`
 WHERE `url_pattern` IN (
   '/prg', '/prg/**',
   '/bbs/**',
   '/file/preview/**',
   '/fileDown/**',
   '/notification/**',
   '/api/v1/sites/*/banners',
   '/api/v1/file/upload/**', '/api/v1/file/upload', '/api/v1/file/**',
   '/api/v1/board/like/**', '/api/v1/board/report/**',
   '/api/v1/notification/**'
 );

INSERT INTO `tb_role_url_access`
  (url_access_id, url_pattern, http_method, access_type, allowed_user_types, required_roles,
   require_csrf_yn, require_2fa_yn, priority, `DESCRIPTION`, use_yn, delete_yn, created_by, created_at)
VALUES

  -- ── 쓰기 경로 — 인증 필수 (priority 를 낮게 둬 조회 규칙보다 먼저 평가) ──────

  ('00000000-0000-7000-8001-000000000101', '/bbs/**', 'POST', 'AUTHENTICATED', NULL, NULL,
   'Y', 'N', 100, '게시판 쓰기 전반(작성·수정·삭제·댓글) — 세밀 권한은 컨트롤러 판정', 'Y', 'N', @actor, current_timestamp()),

  ('00000000-0000-7000-8001-000000000102', '/api/v1/file/upload', 'POST', 'AUTHENTICATED', NULL, NULL,
   'Y', 'N', 100, '파일 업로드 — 익명 업로드 금지(웹쉘 방어 1차 관문)', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000103', '/api/v1/file/upload/**', 'POST', 'AUTHENTICATED', NULL, NULL,
   'Y', 'N', 100, '파일 업로드 유형별(image·document·video) — 익명 업로드 금지', 'Y', 'N', @actor, current_timestamp()),

  ('00000000-0000-7000-8001-000000000104', '/api/v1/board/like/**', 'POST', 'AUTHENTICATED', NULL, NULL,
   'Y', 'N', 110, '좋아요 토글 — 중복 판정에 사용자 식별이 필요하다', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000105', '/api/v1/board/report/**', 'POST', 'AUTHENTICATED', NULL, NULL,
   'Y', 'N', 110, '신고 접수 — 익명 신고는 남용 통로가 된다', 'Y', 'N', @actor, current_timestamp()),

  -- ── 내 정보 성격 — 인증 필수 ────────────────────────────────────────────

  ('00000000-0000-7000-8001-000000000106', '/notification/**', 'ALL', 'AUTHENTICATED', NULL, NULL,
   'Y', 'N', 120, '알림 목록·상세·읽음·수신설정 — 전부 본인 것', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000107', '/api/v1/notification/**', 'ALL', 'AUTHENTICATED', NULL, NULL,
   'N', 'N', 120, '미읽음 개수 폴링(GET) — 상태 변경 없어 CSRF 불요', 'Y', 'N', @actor, current_timestamp()),

  -- ── 공개 조회 ───────────────────────────────────────────────────────────
  --   게시판 목록·상세는 익명 공개다. 비밀글 본문·읽기권한(readAuth)은 컨트롤러가 막는다.

  ('00000000-0000-7000-8001-000000000108', '/bbs/**', 'GET', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 200, '게시판 목록·상세 공개 조회 — 비밀글/읽기권한은 컨트롤러 판정', 'Y', 'N', @actor, current_timestamp()),

  ('00000000-0000-7000-8001-000000000109', '/file/preview/**', 'GET', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 200, '첨부 미리보기(PDF·문서 뷰어) — 공개 게시판 상세에서 호출', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000110', '/fileDown/**', 'GET', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 200, '첨부 다운로드·썸네일 — downloadAuth 판정은 서비스가 한다', 'Y', 'N', @actor, current_timestamp()),

  ('00000000-0000-7000-8001-000000000111', '/api/v1/file/**', 'GET', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 210, '파일 단건·그룹 다운로드(fragments·prg 조각이 호출). 업로드는 위 100번대 규칙이 선점', 'Y', 'N', @actor, current_timestamp()),

  ('00000000-0000-7000-8001-000000000112', '/api/v1/sites/*/banners', 'GET', 'PERMIT_ALL', NULL, NULL,
   'N', 'N', 210, '사이트 배너 공개 조회(레이아웃 클라이언트 슬롯) — 001 실측 규칙 이식', 'Y', 'N', @actor, current_timestamp()),

  -- ── 학과 프로그램 제네릭 쉘 (001 실측 규칙 이식) ─────────────────────────
  --   목록 조각은 secondary 미이식이라 아직 404 다. 쉘 자체는 열어 둔다.

  ('00000000-0000-7000-8001-000000000113', '/prg', 'GET', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 220, '학과 프로그램 루트 공개', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000114', '/prg/**', 'GET', 'PERMIT_ALL', NULL, NULL,
   'Y', 'N', 220, '학과 프로그램(교수진·직원·연구실·수업계획서) 쉘 공개', 'Y', 'N', @actor, current_timestamp());

-- ※ 정적 자원(/css/**, /js/**, /fonts/**, /img/**, /tmpl/**)은 여기 넣지 않는다.
--   SecurityConfig 의 permitAll 로 처리한다 — 인가 판단 이전에 통과해야 한다.
