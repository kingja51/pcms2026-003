-- ============================================================================
-- 통합 로그인 VIEW — v_user_login  (반복 마이그레이션 R__)
-- ============================================================================
-- MEMBER(tb_member) 와 STAFF(tb_admin) 를 하나의 인증 조회 지점으로 합친다.
-- MemberUserDetailsService · AdminUserDetailsService 가 이 뷰만 본다 —
-- 로그인 주체가 늘어도 서비스 코드가 아니라 이 뷰가 바뀐다.
--
-- ── 왜 R__(반복) 인가 ───────────────────────────────────────────────────────
-- 뷰는 스키마가 아니라 **정의 그 자체가 산출물**이다. V__ 로 관리하면 컬럼 하나를
-- 고칠 때마다 새 파일이 쌓이고, 현재 정의를 알려면 파일 전체를 시간순으로 읽어야 한다.
-- R__ 는 체크섬이 바뀔 때마다 재실행되므로 **이 파일 하나가 항상 현재 정의**다.
-- Flyway 는 R__ 를 모든 V__ 이후에 실행한다 — tb_member 가 만들어진 뒤에 돈다.
--
-- ⚠️ V__ 와 달리 R__ 는 **수정해도 된다**(수정이 곧 반영이다). 단 CREATE OR REPLACE
--    를 유지해야 재실행이 성립한다.
--
-- ── 003 변경점 (2026-07-31) ─────────────────────────────────────────────────
-- 001 은 EMPLOYEE(tb_employee) 를 세 번째 UNION 지로 붙였다. 003 은 **로그인 주체를
-- MEMBER·STAFF 2종으로 확정**했다(D7). tb_employee 는 조회 전용이며 로그인·권한을
-- 갖지 않으므로 UNION 에서 제외한다. employee_seq 도 노출하지 않는다 — 남겨 두면
-- "직원도 로그인하나?" 라는 혼동만 만든다.
--
-- ── 컬럼 계약 ───────────────────────────────────────────────────────────────
-- 두 갈래의 컬럼 수·순서·타입이 같아야 UNION ALL 이 성립한다. tb_member 에 없는
-- 관리자 전용 컬럼(2FA·IP 화이트리스트·접속 허용시간)은 상수로 채운다:
--   two_factor_enabled_yn = 'N'  — 회원 2FA 는 도입하지 않았다
--   ip_whitelist / allowed_time_* = NULL — 회원에게는 접속 제한을 두지 않는다
--   role_codes = 'ROLE_MEMBER'   — 회원 인가는 tb_member_role 없이 user_type 으로 한다
--   department_* = ''            — 회원은 부서에 속하지 않는다(NULL 아닌 '' 은 001 실측)
-- ============================================================================

CREATE OR REPLACE VIEW `v_user_login` AS

-- ── 회원 ────────────────────────────────────────────────────────────────────
SELECT 'MEMBER'                  AS `user_type`,
       `m`.`member_id`           AS `user_id`,
       `m`.`member_seq`          AS `uniq_id`,
       `m`.`site_id`             AS `site_id`,
       NULL                      AS `group_id`,
       `m`.`login_id`            AS `login_id`,
       `m`.`PASSWORD`            AS `password`,
       `m`.`STATUS`              AS `status`,
       `m`.`login_fail_count`    AS `login_fail_count`,
       `m`.`locked_until`        AS `locked_until`,
       `m`.`last_login_at`       AS `last_login_at`,
       `m`.`password_changed_at` AS `password_changed_at`,
       `m`.`password_expire_at`  AS `password_expire_at`,
       'N'                       AS `two_factor_enabled_yn`,
       NULL                      AS `two_factor_secret`,
       NULL                      AS `ip_whitelist`,
       NULL                      AS `allowed_time_from`,
       NULL                      AS `allowed_time_to`,
       `m`.`role_ids`            AS `role_ids`,
       'ROLE_MEMBER'             AS `role_codes`,
       `m`.`group_ids`           AS `group_ids`,
       ''                        AS `department_id`,
       ''                        AS `department_name`,
       `m`.`delete_yn`           AS `delete_yn`
  FROM `tb_member` `m`
 WHERE `m`.`delete_yn` = 'N'

UNION ALL

-- ── 관리자 ──────────────────────────────────────────────────────────────────
SELECT 'STAFF'                   AS `user_type`,
       `a`.`admin_id`            AS `user_id`,
       `a`.`admin_seq`           AS `uniq_id`,
       NULL                      AS `site_id`,
       `a`.`admin_group_id`      AS `group_id`,
       `a`.`login_id`            AS `login_id`,
       `a`.`PASSWORD`            AS `password`,
       `a`.`STATUS`              AS `status`,
       `a`.`login_fail_count`    AS `login_fail_count`,
       `a`.`locked_until`        AS `locked_until`,
       `a`.`last_login_at`       AS `last_login_at`,
       `a`.`password_changed_at` AS `password_changed_at`,
       `a`.`password_expire_at`  AS `password_expire_at`,
       `a`.`two_factor_enabled_yn` AS `two_factor_enabled_yn`,
       `a`.`two_factor_secret`   AS `two_factor_secret`,
       `a`.`ip_whitelist`        AS `ip_whitelist`,
       `a`.`allowed_time_from`   AS `allowed_time_from`,
       `a`.`allowed_time_to`     AS `allowed_time_to`,
       `a`.`role_ids`            AS `role_ids`,
       `a`.`role_codes`          AS `role_codes`,
       `a`.`group_ids`           AS `group_ids`,
       `a`.`department_id`       AS `department_id`,
       `a`.`department_name`     AS `department_name`,
       `a`.`delete_yn`           AS `delete_yn`
  FROM `tb_admin` `a`
 WHERE `a`.`delete_yn` = 'N';
