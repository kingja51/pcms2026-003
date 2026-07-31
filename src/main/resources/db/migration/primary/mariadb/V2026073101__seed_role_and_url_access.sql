-- ============================================================================
-- P2 참조 데이터 시드 — 역할(tb_role) + 접근 규칙(tb_role_url_access)
-- ============================================================================
-- 개발가이드 §6-5: 시드 데이터는 마이그레이션에 넣지 않는다. 단 **참조 데이터**
-- (공통코드, tb_role_url_access 기본 규칙 등 없으면 앱이 동작하지 않는 것)는 포함한다.
-- 이 파일이 없으면 DynamicAuthorizationManager 가 무매칭 DENY 로 모든 화면을 막는다.
--
-- ⚠️ 적용된 마이그레이션 파일은 수정하지 않는다(체크섬 불일치로 기동 실패).
--    규칙을 바꾸려면 새 버전 파일을 추가한다.
--
-- 범위: P2(관리자 인증)에 필요한 최소 규칙만 넣는다.
--   001 라이브에는 265건이 있으나 대부분 아직 만들지 않은 도메인용이다.
--   도메인을 추가할 때마다 해당 페이즈에서 규칙을 추가한다(상시 게이트 3).
--
-- 003 변경점:
--   · ROLE_EMPLOYEE 제외 — 직원은 로그인·권한 주체가 아니다(D7, 2026-07-31)
--   · allowed_user_types 에서 EMPLOYEE 제거 — v_user_login 은 MEMBER·STAFF 2종
-- ============================================================================

-- ── 역할 7종 ────────────────────────────────────────────────────────────────
-- role_code 는 tb_admin.role_codes(CSV) 및 DynamicAuthorizationManager 매칭에 쓰인다.
-- ※ ROLL_EDITOR / ROLL_MANAGER / ROLL_PRIVACY 는 001 실측 그대로다(ROLE_ 오타).
--   지금 고치면 값이 쌓이기 전이라 비용이 가장 싸지만, 데이터 계약 변경이므로
--   임의로 바꾸지 않았다. PLAN §7 에 결정 항목으로 기록했다.
INSERT INTO `tb_role`
  (role_id, role_code, role_name, DESCRIPTION, sort_order, use_yn, delete_yn, created_by, created_at)
VALUES
  ('00000000-0000-7000-8000-000000000011', 'ROLE_SUPER',   '슈퍼관리자',     '전체 권한',                    10, 'Y', 'N', 'SYSTEM', current_timestamp()),
  ('00000000-0000-7000-8000-000000000012', 'ROLE_ADMIN',   '관리자',         '운영 전반',                    20, 'Y', 'N', 'SYSTEM', current_timestamp()),
  ('00000000-0000-7000-8000-000000000013', 'ROLL_EDITOR',  '편집자',         '콘텐츠·게시판 편집',           30, 'Y', 'N', 'SYSTEM', current_timestamp()),
  ('00000000-0000-7000-8000-000000000014', 'ROLE_STAFF',   '서브관리자',     '관리자 화면 기본 접근',        40, 'Y', 'N', 'SYSTEM', current_timestamp()),
  ('00000000-0000-7000-8000-000000000015', 'ROLE_MEMBER',  '회원',           '사용자 사이트 로그인 주체',    50, 'Y', 'N', 'SYSTEM', current_timestamp()),
  ('00000000-0000-7000-8000-000000000017', 'ROLL_MANAGER', '책임자',         '승인 권한',                    60, 'Y', 'N', 'SYSTEM', current_timestamp()),
  ('00000000-0000-7000-8000-000000000018', 'ROLL_PRIVACY', '개인정보관리자', '개인정보 조회·파기 권한',      70, 'Y', 'N', 'SYSTEM', current_timestamp());

-- ── 접근 규칙 ───────────────────────────────────────────────────────────────
-- priority ASC 로 평가하고 **처음 매칭된 규칙**이 판정한다. 무매칭이면 DENY.
--   PERMIT_ALL    : 익명 허용
--   AUTHENTICATED : 인증만 요구
--   ROLE          : required_roles(CSV) 중 하나 이상 보유
INSERT INTO `tb_role_url_access`
  (url_access_id, url_pattern, http_method, access_type, allowed_user_types, required_roles,
   require_csrf_yn, require_2fa_yn, priority, DESCRIPTION, use_yn, delete_yn, created_by, created_at)
VALUES
  -- 로그인 진입점 — 익명이 들어와야 한다
  ('00000000-0000-7000-8001-000000000001', '/admin/login',  'ALL', 'PERMIT_ALL',    NULL,    NULL,
   'Y', 'N',  20, '관리자 로그인 페이지', 'Y', 'N', 'SYSTEM', current_timestamp()),
  ('00000000-0000-7000-8001-000000000002', '/admin/logout', 'ALL', 'PERMIT_ALL',    'STAFF', NULL,
   'Y', 'N',  20, '관리자 로그아웃 (001 의 STAFF,EMPLOYEE 에서 EMPLOYEE 제거)', 'Y', 'N', 'SYSTEM', current_timestamp()),

  -- 2FA 화면 — 1차 인증은 끝났지만 2FA 미완료 상태로 접근해야 한다
  ('00000000-0000-7000-8001-000000000003', '/admin/2fa/**', 'ALL', 'AUTHENTICATED', 'STAFF', NULL,
   'Y', 'N',  30, '관리자 2단계 인증 화면', 'Y', 'N', 'SYSTEM', current_timestamp()),

  -- CSP 위반 리포트 — 브라우저가 인증 없이 POST 한다
  ('00000000-0000-7000-8001-000000000004', '/csp-report',   'POST', 'PERMIT_ALL',   NULL,    NULL,
   'N', 'N',  30, 'CSP 위반 리포트 수집 (CSRF 예외 — 브라우저 자동 전송)', 'Y', 'N', 'SYSTEM', current_timestamp()),

  -- 관리자 화면 — 시스템 영역을 먼저 평가하고, 나머지는 포괄 규칙으로 받는다
  ('00000000-0000-7000-8001-000000000005', '/admin/system/**', 'ALL', 'ROLE', 'STAFF',
   '00000000-0000-7000-8000-000000000014',
   'Y', 'Y', 9000, '관리자 시스템 영역 — 2FA 필수', 'Y', 'N', 'SYSTEM', current_timestamp()),
  ('00000000-0000-7000-8001-000000000006', '/admin/**',        'ALL', 'ROLE', 'STAFF',
   '00000000-0000-7000-8000-000000000014',
   'Y', 'Y', 9100, '관리자 화면 포괄 — 2FA 필수', 'Y', 'N', 'SYSTEM', current_timestamp());

-- ※ 정적 자원(/css/**, /js/**, /fonts/**, /img/**, /tmpl/**)은 여기 넣지 않는다.
--   SecurityConfig 의 permitAll 로 처리한다 — 인가 판단 이전에 통과해야 한다.
--   001 에서 /fonts/** 누락으로 폰트가 조용히 시스템 폰트로 폴백된 이력이 있다.
--
-- ※ 무매칭 DENY 는 DynamicAuthorizationManager 의 기본 동작이다.
--   fallback 행(priority 9999)을 따로 두지 않는다 — 001 라이브에도 없다.
