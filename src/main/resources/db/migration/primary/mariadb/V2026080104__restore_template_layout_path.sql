-- ============================================================================
-- tb_template 을 코드가 기대하는 형태로 되돌린다 (D13 — A안, 2026-08-01)
-- ============================================================================
-- 베이스라인 DDL 의 tb_template 은 `default_layout_id` FK → `tb_layout` **3단 구조**로
-- 재설계돼 있었다. 그런데 이식한 코드는 001 형태(단일 문자열 컬럼) 그대로다:
--
--   · 매퍼 **17곳**이 `tb_template.layout_path` 를 SELECT 한다
--     (SiteContext·Menu·Content·BbsMaster·Schedule·Survey·Site·SiteTemplate·Template)
--   · `TemplateMapper.insert` 는 `layout_path`·`file_group_id` 를 INSERT 한다
--   · 신설된 `tb_layout`·`tb_theme` 를 **읽는 코드는 0건**이다
--
-- 그 결과 `SiteContextService.getContextByCode()` 가 SQL 오류로 죽어
-- **모든 사용자 사이트 페이지가 렌더되지 않았다.**
--
-- 사용자 결정(2026-08-01): **A안 — 스키마를 코드에 맞춘다.**
--   코드 17곳을 고치는 대신 컬럼을 복원한다. `default_layout_id` 와 `layout_path` 로
--   진실이 둘이 되는 것은 감수한다. 대신 아래 ③ 주석에 관계를 명시해 둔다.
--
-- ⚠️ 적용된 마이그레이션은 수정하지 않는다(체크섬 불일치로 기동 실패).
-- ============================================================================


-- ── ① layout_path 복원 ──────────────────────────────────────────────────────
--    Thymeleaf 레이아웃 뷰 경로. 001 실측은 varchar(500) NOT NULL 이었다.
--    기존 행에 값을 채워야 NOT NULL 을 걸 수 있으므로 **nullable 로 추가 → backfill
--    → NOT NULL 승격** 3단계로 간다. 한 번에 NOT NULL 로 추가하면 기존 행이 있을 때
--    MariaDB 가 빈 문자열을 넣어 버려(strict 모드가 아니면) 조용히 깨진다.
ALTER TABLE `tb_template`
  ADD COLUMN `layout_path` varchar(500) NULL COMMENT 'Thymeleaf 레이아웃 경로 (예: front/layouts/KRDS/krds)'
  AFTER `template_name`;

-- ── ② file_group_id 복원 ────────────────────────────────────────────────────
--    템플릿 캡처 이미지 등 첨부. TemplateMapper 가 SELECT·INSERT 한다.
--    FK 는 걸지 않는다 — 001 도 걸지 않았고, 파일 그룹이 먼저 지워질 수 있다.
ALTER TABLE `tb_template`
  ADD COLUMN `file_group_id` varchar(40) NULL COMMENT '캡처 이미지 등 첨부 파일 그룹 ID'
  AFTER `design_md`;


-- ── ③ 기존 행 backfill ──────────────────────────────────────────────────────
--    뷰 경로 규칙: templates/front/layouts/{layout_code}/{layout_code 소문자}.html
--    실측 6종(KRDS·IBM·AIRBNB·CLAY·EMPTY·VIEWER) 모두 이 규칙을 따른다.
--
--    이 시점에 tb_template 이 비어 있으면(신규 설치) 아무 행도 안 바뀐다 — 정상이다.
UPDATE `tb_template` t
  JOIN `tb_layout` l ON l.`layout_id` = t.`default_layout_id`
   SET t.`layout_path` = CONCAT('front/layouts/', l.`layout_code`, '/', LOWER(l.`layout_code`))
 WHERE t.`layout_path` IS NULL;

--    default_layout_id 가 가리키는 레이아웃이 없는 행은 폴백으로 채운다.
--    (FK 가 있어 정상적으로는 발생하지 않지만, NOT NULL 승격을 막지 않도록 둔다)
UPDATE `tb_template`
   SET `layout_path` = 'front/layouts/EMPTY/empty'
 WHERE `layout_path` IS NULL;


-- ── ④ NOT NULL 승격 ─────────────────────────────────────────────────────────
--    코드가 이 값을 무조건 읽는다. NULL 이면 layoutPath 가 null 이 되고
--    화면은 EMPTY 폴백으로 조용히 떨어진다 — 원인을 찾기 어려운 실패다.
ALTER TABLE `tb_template`
  MODIFY COLUMN `layout_path` varchar(500) NOT NULL COMMENT 'Thymeleaf 레이아웃 경로 (예: front/layouts/KRDS/krds)';


-- ── ⑤ default_layout_id 를 nullable 로 완화 ─────────────────────────────────
--    **이게 없으면 A안이 반쪽짜리가 된다.**
--    `TemplateMapper.insert` 는 default_layout_id 를 넣지 않는다(DTO 에 필드 자체가 없다).
--    NOT NULL 로 두면 관리자 화면에서 템플릿을 새로 만들 때마다 INSERT 가 실패한다.
--
--    FK 는 유지한다 — 값이 있으면 여전히 tb_layout 을 가리켜야 한다.
--    3단 구조로 가는 B안을 나중에 선택하더라도 이 FK 가 남아 있어야 되돌리기 쉽다.
ALTER TABLE `tb_template`
  MODIFY COLUMN `default_layout_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '기본 레이아웃 (tb_layout) — 참고용. 실제 렌더 경로는 layout_path 가 결정한다';


-- ============================================================================
--  진실이 둘인 상태에 대한 메모
--
--  layout_path 와 default_layout_id 가 같은 것을 가리키지만 **동기화는 없다.**
--  현재 코드는 layout_path 만 읽으므로 default_layout_id 는 사실상 참고 컬럼이다.
--
--  · 지금: layout_path 가 진실. tb_layout·tb_theme 는 데이터만 있고 읽는 코드가 없다
--  · 나중에 3단 구조(B안)로 갈 때: 매퍼 17곳 + TemplateSaveForm ·
--    TemplateMngController · 관리자 폼(admin/system/template/form.html)을 함께 고친 뒤
--    layout_path 를 드롭한다
--
--  PLAN §7 에 결정 근거와 B안 전환 시 손댈 목록을 기록해 두었다.
-- ============================================================================
