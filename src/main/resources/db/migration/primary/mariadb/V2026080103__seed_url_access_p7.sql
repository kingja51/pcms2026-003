-- ============================================================================
-- P7 접근 규칙 시드 — Actuator
-- ============================================================================
-- 로그 뷰어·통계 화면은 `/admin/system/**` 이라 P2 의 포괄 규칙이 이미 받는다.
-- 여기서 다룰 것은 **Actuator** 뿐이다.
--
-- ⚠️ **현재 Actuator 는 전부 막혀 있다.** `/actuator/**` 규칙이 없어 무매칭 DENY 가
--    걸린다 — health 조차 열리지 않는다. 로드밸런서·컨테이너 헬스체크가 이걸 찌르므로
--    운영에서 인스턴스가 계속 unhealthy 로 판정될 수 있다.
--
-- 정책(PLAN P7): **공개는 health · info · prometheus 만**, 나머지는 ROLE_ADMIN.
--   · health     — LB/오케스트레이터 헬스체크. 익명 필요.
--                  상세(`show-details`)는 yml 이 `when-authorized` 라 익명에게는
--                  UP/DOWN 만 보인다 — 내부 구성요소 이름이 새지 않는다
--   · info       — 빌드 정보. 민감하지 않다
--   · prometheus — 메트릭 수집기가 주기적으로 긁는다. 인증을 요구하면 수집이 끊긴다
--   · 그 외(metrics·httpexchanges 등) — httpexchanges 는 **최근 요청의 URI·헤더**를
--                  담아 사실상 접속 로그다. 반드시 관리자 전용이어야 한다
--
-- priority 는 개별 3건을 포괄 규칙보다 먼저 평가하도록 낮게 둔다.
-- ============================================================================

SET @actor := 'SYSTEM';

DELETE FROM `tb_role_url_access`
 WHERE `url_pattern` IN (
   '/actuator/health', '/actuator/health/**',
   '/actuator/info',
   '/actuator/prometheus',
   '/actuator/**'
 );

INSERT INTO `tb_role_url_access`
  (url_access_id, url_pattern, http_method, access_type, allowed_user_types, required_roles,
   require_csrf_yn, require_2fa_yn, priority, `DESCRIPTION`, use_yn, delete_yn, created_by, created_at)
VALUES

  -- ── 공개 3종 ────────────────────────────────────────────────────────────

  ('00000000-0000-7000-8001-000000000401', '/actuator/health', 'GET', 'PERMIT_ALL', NULL, NULL,
   'N', 'N', 150, 'LB·오케스트레이터 헬스체크 — 상세는 when-authorized 라 익명은 UP/DOWN 만 본다', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000402', '/actuator/health/**', 'GET', 'PERMIT_ALL', NULL, NULL,
   'N', 'N', 150, '헬스 그룹(liveness·readiness)', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000403', '/actuator/info', 'GET', 'PERMIT_ALL', NULL, NULL,
   'N', 'N', 150, '빌드 정보 — 민감하지 않다', 'Y', 'N', @actor, current_timestamp()),
  ('00000000-0000-7000-8001-000000000404', '/actuator/prometheus', 'GET', 'PERMIT_ALL', NULL, NULL,
   'N', 'N', 150, '메트릭 수집 — 인증을 요구하면 수집이 끊긴다', 'Y', 'N', @actor, current_timestamp()),

  -- ── 나머지 전부 관리자 ──────────────────────────────────────────────────
  -- httpexchanges 는 최근 요청의 URI·헤더를 담아 사실상 접속 로그다.

  ('00000000-0000-7000-8001-000000000405', '/actuator/**', 'ALL', 'ROLE', 'STAFF',
   '00000000-0000-7000-8000-000000000012',
   'Y', 'N', 9300, 'Actuator 나머지 — metrics·httpexchanges 등은 ROLE_ADMIN 전용', 'Y', 'N', @actor, current_timestamp());

-- ※ 운영에서 prometheus 를 사설망에만 노출하려면 이 행을 IP_ONLY 로 바꾸는 대신
--   리버스 프록시에서 막는 편이 낫다 — 인가 계층은 애플리케이션 밖 경로를 모른다.
