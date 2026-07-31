-- ============================================================================
--  PCMS 2026-003 — 데모 사이트 5종 시드
--  대상 DB: pcms2026-003-primary (MariaDB)
-- ----------------------------------------------------------------------------
--  ⚠️ **이 파일은 Flyway 마이그레이션이 아니다.** `db/migration/` 에는 참조 데이터
--     (공통코드·기본 접근 규칙)만 넣는다. 데모 데이터를 마이그레이션에 넣으면
--     **운영 배포에 그대로 딸려 간다**(개발가이드 §6-5).
--     운영에 넣을 사이트는 이 파일을 복사해 값을 바꿔 손으로 적용한다.
--
--  선행: sql/pcms2026_primary_db.sql (스키마) + Flyway V2026073101~ (역할·접근규칙)
--  적용: mysql -u <user> -p pcms2026-003-primary < sql/seed_demo_sites.sql
--  멱등: 각 블록이 DELETE 후 INSERT. 여러 번 돌려도 같은 상태가 된다.
--
--  절차 설명은 개발가이드 §18(사이트 추가 절차 — 복제 레시피).
-- ============================================================================
--
--  ⚠️ **선행 필수: Flyway V2026080104·V2026080105 가 적용돼 있어야 한다.**
--
--  베이스라인 DDL 의 `tb_template` 에는 `layout_path` 컬럼이 없다. 매퍼 17곳이
--  그것을 SELECT 하므로 그대로는 `SiteContextService` 가 SQL 오류로 죽고
--  **모든 사용자 사이트 페이지가 렌더되지 않는다.**
--
--  V2026080104 가 `layout_path`·`file_group_id` 를 복원하고
--  `default_layout_id` 를 nullable 로 완화한다(D13 — A안, 2026-08-01 사용자 결정).
--  V2026080105 는 tb_site 에 `theme`(테마 코드 문자열)를 추가한다.
--  적용 전에 이 파일을 돌리면 "Unknown column 'layout_path'" 또는
--  "Unknown column 'theme'" 로 멈춘다 — 그게 곧 선행이 안 됐다는 신호다.
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @actor := 'SEED';
SET @ip    := '127.0.0.1';

-- ════════════════════════════════════════════════════════════════════════════
--  ① 레이아웃 — 뷰 폴더와 1:1
--     뷰 경로 규칙: templates/front/layouts/{CODE}/{code소문자}.html
--     실측 6종 중 데모용 5종을 등록한다(VIEWER 는 문서 뷰어 전용이라 제외).
-- ════════════════════════════════════════════════════════════════════════════

DELETE FROM `tb_layout` WHERE `layout_id` LIKE 'LAY_DEMO%';

INSERT INTO `tb_layout`
  (layout_id, layout_code, layout_name, wireframe_ref, description,
   sort_order, use_yn, delete_yn, created_by, created_ip, created_at)
VALUES
  ('LAY_DEMO_KRDS',   'KRDS',   'KRDS 표준',   NULL, '행정안전부 KRDS 디자인 시스템 — 공공기관 기본', 10, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('LAY_DEMO_IBM',    'IBM',    'IBM Carbon',  NULL, 'IBM Carbon 계열 — 데이터 밀도가 높은 화면',     20, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('LAY_DEMO_AIRBNB', 'AIRBNB', 'Airbnb 계열', NULL, '카드·여백 중심 — 이미지가 많은 사이트',        30, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('LAY_DEMO_CLAY',   'CLAY',   'Clay 계열',   NULL, '뉴모피즘 — 부드러운 그림자 대비',              40, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('LAY_DEMO_EMPTY',  'EMPTY',  '민짜(폴백)',  NULL, '레이아웃 미지정 시 폴백. 헤더·푸터 없음',      90, 'Y', 'N', @actor, @ip, current_timestamp());

-- ════════════════════════════════════════════════════════════════════════════
--  ② 템플릿 — layout_path 는 뷰 경로 문자열(V2026080104 가 복원한 컬럼)
-- ════════════════════════════════════════════════════════════════════════════

DELETE FROM `tb_template` WHERE `template_id` LIKE 'TPL_DEMO%';

INSERT INTO `tb_template`
  (template_id, template_code, template_name, default_layout_id, layout_path,
   description, use_yn, delete_yn, created_by, created_ip, created_at)
VALUES
  ('TPL_DEMO_KRDS',   'krds',   'KRDS 표준',   'LAY_DEMO_KRDS',   'front/layouts/KRDS/krds',     '공공기관 기본', 'Y', 'N', @actor, @ip, current_timestamp()),
  ('TPL_DEMO_IBM',    'ibm',    'IBM Carbon',  'LAY_DEMO_IBM',    'front/layouts/IBM/ibm',       '데이터 밀도',   'Y', 'N', @actor, @ip, current_timestamp()),
  ('TPL_DEMO_AIRBNB', 'airbnb', 'Airbnb 계열', 'LAY_DEMO_AIRBNB', 'front/layouts/AIRBNB/airbnb', '이미지 중심',   'Y', 'N', @actor, @ip, current_timestamp()),
  ('TPL_DEMO_CLAY',   'clay',   'Clay 계열',   'LAY_DEMO_CLAY',   'front/layouts/CLAY/clay',     '뉴모피즘',      'Y', 'N', @actor, @ip, current_timestamp()),
  ('TPL_DEMO_EMPTY',  'empty',  '민짜(폴백)',  'LAY_DEMO_EMPTY',  'front/layouts/EMPTY/empty',   '폴백 전용',     'Y', 'N', @actor, @ip, current_timestamp());

-- ════════════════════════════════════════════════════════════════════════════
--  ③ 테마 — 템플릿별 색 변주. css_class 는 <html> 에 붙는다.
--     tb_site 의 FK 가 (template_id, theme_id) 복합이라 테마는 템플릿에 종속이다.
-- ════════════════════════════════════════════════════════════════════════════

DELETE FROM `tb_theme` WHERE `theme_id` LIKE 'THM_DEMO%';

INSERT INTO `tb_theme`
  (theme_id, template_id, theme_code, theme_name, css_class,
   sort_order, use_yn, delete_yn, created_by, created_ip, created_at)
VALUES
  ('THM_DEMO_KRDS',   'TPL_DEMO_KRDS',   'default', '기본', '',            10, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('THM_DEMO_IBM',    'TPL_DEMO_IBM',    'blue',    '블루', 'theme-blue',  10, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('THM_DEMO_AIRBNB', 'TPL_DEMO_AIRBNB', 'default', '기본', '',            10, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('THM_DEMO_CLAY',   'TPL_DEMO_CLAY',   'default', '기본', '',            10, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('THM_DEMO_EMPTY',  'TPL_DEMO_EMPTY',  'default', '기본', '',            10, 'Y', 'N', @actor, @ip, current_timestamp());

-- ════════════════════════════════════════════════════════════════════════════
--  ④ 사이트 5종
--     site_code 는 예약어를 피한다(개발가이드 §18) —
--     admin·member·bbs·prg·api·file·notification·survey·complaint·schedule·holiday·actuator 등.
-- ════════════════════════════════════════════════════════════════════════════

DELETE FROM `tb_site` WHERE `site_id` LIKE 'SIT_DEMO%';

--  컬럼명은 DDL 원본 `template_id` 다(2026-08-01 사용자 결정 — 코드 쪽을 맞췄다).
--  `theme` 은 테마 **코드 문자열**, `theme_id` 는 FK 로 서로 다른 컬럼이다 — 둘 다 채운다.
INSERT INTO `tb_site`
  (site_id, site_code, site_name, template_id, theme_id, theme,
   sort_order, use_yn, delete_yn, created_by, created_ip, created_at)
VALUES
  ('SIT_DEMO_MAIN',  'main',  '대표 사이트',   'TPL_DEMO_KRDS',   'THM_DEMO_KRDS',   'default', 10, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('SIT_DEMO_PORTAL','portal','통합 포털',     'TPL_DEMO_IBM',    'THM_DEMO_IBM',    'blue',    20, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('SIT_DEMO_TOUR',  'tour',  '관광 사이트',   'TPL_DEMO_AIRBNB', 'THM_DEMO_AIRBNB', 'default', 30, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('SIT_DEMO_CULT',  'cult',  '문화 사이트',   'TPL_DEMO_CLAY',   'THM_DEMO_CLAY',   'default', 40, 'Y', 'N', @actor, @ip, current_timestamp()),
  ('SIT_DEMO_PLAIN', 'plain', '민짜 데모',     'TPL_DEMO_EMPTY',  'THM_DEMO_EMPTY',  'default', 90, 'Y', 'N', @actor, @ip, current_timestamp());

-- ════════════════════════════════════════════════════════════════════════════
--  ⑤ 접근 규칙 — **사이트마다 2행**. 빠뜨리면 로그인 페이지로 리다이렉트된다.
--
--     catch-all `/*` 을 쓰지 않는 이유: 등록되지 않은 site_code 까지 열린다.
--     priority 는 300 — P4~P7 의 개별 규칙(100~240)보다 뒤, 관리자 포괄(9000+)보다 앞.
-- ════════════════════════════════════════════════════════════════════════════

DELETE FROM `tb_role_url_access` WHERE `url_access_id` LIKE 'URA_DEMO%';

INSERT INTO `tb_role_url_access`
  (url_access_id, url_pattern, http_method, access_type, allowed_user_types, required_roles,
   require_csrf_yn, require_2fa_yn, priority, `DESCRIPTION`, use_yn, delete_yn, created_by, created_ip, created_at)
VALUES
  ('URA_DEMO_MAIN_1',  '/main',      'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 main 루트',   'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_MAIN_2',  '/main/**',   'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 main 하위',   'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_PORT_1',  '/portal',    'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 portal 루트', 'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_PORT_2',  '/portal/**', 'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 portal 하위', 'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_TOUR_1',  '/tour',      'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 tour 루트',   'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_TOUR_2',  '/tour/**',   'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 tour 하위',   'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_CULT_1',  '/cult',      'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 cult 루트',   'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_CULT_2',  '/cult/**',   'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 cult 하위',   'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_PLAIN_1', '/plain',     'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 plain 루트',  'Y','N', @actor, @ip, current_timestamp()),
  ('URA_DEMO_PLAIN_2', '/plain/**',  'GET', 'PERMIT_ALL', NULL, NULL, 'Y','N', 300, '데모 사이트 plain 하위',  'Y','N', @actor, @ip, current_timestamp());

-- ════════════════════════════════════════════════════════════════════════════
--  ⑥ 메뉴 — 사이트마다 홈·소개·오시는길·사이트맵
--     "URL이 진실" — 메뉴에 없는 slug 는 커스텀 뷰가 없는 한 404 다.
--     link_url 은 DefaultUsrController 의 findByLinkUrl 조회 키다.
-- ════════════════════════════════════════════════════════════════════════════

DELETE FROM `tb_menu` WHERE `menu_id` LIKE 'MNU_DEMO%';

INSERT INTO `tb_menu`
  (menu_id, site_id, parent_menu_id, menu_name, menu_type, link_url,
   sort_order, depth, use_yn, delete_yn, created_by, created_ip, created_at)
VALUES
  -- main
  ('MNU_DEMO_MAIN_1', 'SIT_DEMO_MAIN', NULL, '홈',        'URL', '/main/home',     10, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_MAIN_2', 'SIT_DEMO_MAIN', NULL, '소개',      'URL', '/main/about',    20, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_MAIN_3', 'SIT_DEMO_MAIN', NULL, '오시는 길', 'URL', '/main/location', 30, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_MAIN_4', 'SIT_DEMO_MAIN', NULL, '사이트맵',  'URL', '/main/sitemap',  90, 1, 'Y','N', @actor, @ip, current_timestamp()),
  -- portal
  ('MNU_DEMO_PORT_1', 'SIT_DEMO_PORTAL', NULL, '홈',        'URL', '/portal/home',     10, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_PORT_2', 'SIT_DEMO_PORTAL', NULL, '소개',      'URL', '/portal/about',    20, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_PORT_3', 'SIT_DEMO_PORTAL', NULL, '사이트맵',  'URL', '/portal/sitemap',  90, 1, 'Y','N', @actor, @ip, current_timestamp()),
  -- tour
  ('MNU_DEMO_TOUR_1', 'SIT_DEMO_TOUR', NULL, '홈',        'URL', '/tour/home',     10, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_TOUR_2', 'SIT_DEMO_TOUR', NULL, '소개',      'URL', '/tour/about',    20, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_TOUR_3', 'SIT_DEMO_TOUR', NULL, '사이트맵',  'URL', '/tour/sitemap',  90, 1, 'Y','N', @actor, @ip, current_timestamp()),
  -- cult
  ('MNU_DEMO_CULT_1', 'SIT_DEMO_CULT', NULL, '홈',        'URL', '/cult/home',     10, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_CULT_2', 'SIT_DEMO_CULT', NULL, '소개',      'URL', '/cult/about',    20, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_CULT_3', 'SIT_DEMO_CULT', NULL, '사이트맵',  'URL', '/cult/sitemap',  90, 1, 'Y','N', @actor, @ip, current_timestamp()),
  -- plain
  ('MNU_DEMO_PLAIN_1','SIT_DEMO_PLAIN', NULL, '홈',       'URL', '/plain/home',    10, 1, 'Y','N', @actor, @ip, current_timestamp()),
  ('MNU_DEMO_PLAIN_2','SIT_DEMO_PLAIN', NULL, '사이트맵', 'URL', '/plain/sitemap', 90, 1, 'Y','N', @actor, @ip, current_timestamp());

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
--  확인 (개발가이드 §18)
--    GET /main          → 302 → /main/home
--    GET /main/home     → 200, KRDS 레이아웃      (EMPTY 로 보이면 ②가 안 붙은 것)
--    GET /main/sitemap  → 200, 메뉴 4개           (비면 ⑥이 안 들어간 것)
--    로그인 페이지로 튕기면 ⑤가 빠진 것이다.
-- ============================================================================
