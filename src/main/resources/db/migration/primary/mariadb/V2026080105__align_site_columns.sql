-- ============================================================================
-- tb_site 템플릿·테마·레이아웃 컬럼 — 주석 정합 (2026-08-01 사용자 확정)
-- ============================================================================
-- 확정 형태:
--
--   template_id varchar(40) NULL  선택 템플릿 (NULL=미선택 → krds 기본 템플릿 폴백)
--   theme_id    varchar(40) NULL  선택 테마   (NULL=템플릿 기본 브랜드)
--   layout_id   varchar(40) NULL  선택 레이아웃 (NULL=템플릿 기본 레이아웃)
--
--   fk_site_template (template_id)           → tb_template
--   fk_site_theme    (template_id, theme_id) → tb_theme    ← 복합 FK 로 소속 검증
--   fk_site_layout   (layout_id)             → tb_layout
--
-- ⚠️ **구조는 이미 이대로다.** 세 컬럼도 세 FK 도 베이스라인 DDL 에 전부 있다.
--    처음에는 layout_id 를 신설하려 했는데, 확인해 보니 이미 있었다 —
--    그대로 뒀으면 "Duplicate column name 'layout_id'" 로 마이그레이션이 깨졌다.
--    그래서 이 파일은 **주석만 확정 문구로 맞춘다.** DDL 주석도 운영 정책 문서다.
--
-- ── 문제는 스키마가 아니라 코드였다 ────────────────────────────────────────
--    매퍼가 존재하지 않는 컬럼을 SELECT 하고 있었다:
--      · s.default_template_id (19곳)  → 실제 컬럼은 template_id
--      · s.theme               ( 2곳)  → 실제 컬럼은 theme_id (FK)
--    layout_id 는 **아무도 읽지 않는 죽은 컬럼**이었다.
--
--    코드를 스키마에 맞췄다(사용자 결정). 매퍼 SQL·Java 프로퍼티·관리자 화면까지.
--    같은 종류의 결함을 다시 놓치지 않도록 `MapperSchemaConsistencyTest` 로 고정했다 —
--    컴파일도 ArchUnit 도 못 잡는 종류라 테스트 53건이 통과하는 채로 살아 있었다.
--
-- ⚠️ theme_id 를 CSS 클래스에 그대로 쓰면 안 된다.
--    SiteContextModelAdvice 는 `^[a-z][a-z0-9-]{1,30}$` 를 통과한 값에만
--    "theme-" 접두를 붙인다. theme_id(THM_…)는 대문자·언더스코어라 검사에 걸려
--    null 이 되고 **테마가 조용히 미적용**된다.
--    → 매퍼가 tb_theme 를 복합 FK 와 같은 기준으로 LEFT JOIN 해
--      `theme_code` 를 `theme` 별칭으로 노출한다. DB 의 진실은 theme_id 하나다.
--
-- ⚠️ 적용된 마이그레이션은 수정하지 않는다(체크섬 불일치로 기동 실패).
-- ============================================================================

ALTER TABLE `tb_site`
  MODIFY COLUMN `template_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    COMMENT '선택 템플릿 (NULL=미선택 → krds 기본 템플릿 폴백)';

ALTER TABLE `tb_site`
  MODIFY COLUMN `theme_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    COMMENT '선택 테마 (NULL=템플릿 기본 브랜드. 소속 검증=fk_site_theme 복합 FK)';

ALTER TABLE `tb_site`
  MODIFY COLUMN `layout_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    COMMENT '선택 레이아웃 (NULL=템플릿 기본 레이아웃)';


-- ============================================================================
--  적용 후 확인
--    · /bbs/{sc}/{bbsCode} 가 500 이 아니어야 한다(사이트가 없으면 404)
--    · /{siteCode}/home 이 레이아웃과 함께 렌더되고, 테마 지정 시
--      <html> 에 theme-{code} 클래스가 붙어야 한다
--
--  남은 "진실이 둘" 은 tb_template.layout_path ↔ default_layout_id 한 쌍이다
--  (V2026080104 가 코드에 맞춰 복원). tb_site 쪽은 이제 FK 단일이다.
-- ============================================================================
