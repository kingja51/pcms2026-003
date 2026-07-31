
USE `pcms2026-003-logging`;

/*Table structure for table `log_access` */

DROP TABLE IF EXISTS `log_access`;

CREATE TABLE `log_access` (
  `log_access_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `site_id` varchar(40) DEFAULT NULL COMMENT 'SiteContext 해석 결과 (NULL=해석 불가)',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `menu_id` varchar(40) DEFAULT NULL COMMENT '요청 시점의 메뉴 ID (request_uri 의 ?menuId= 파싱 회피용)',
  `actor_user_id` varchar(40) DEFAULT NULL COMMENT '비로그인 NULL',
  `actor_user_type` varchar(20) DEFAULT NULL COMMENT 'MEMBER|ADMIN|ANONYMOUS',
  `actor_login_id` varchar(50) DEFAULT NULL,
  `request_uri` varchar(500) NOT NULL,
  `http_method` varchar(10) NOT NULL,
  `query_string` varchar(500) DEFAULT NULL,
  `referer` varchar(500) DEFAULT NULL,
  `client_ip` varchar(50) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `status_code` int(11) DEFAULT NULL COMMENT '200/302/403/404/500 ...',
  `response_ms` int(11) DEFAULT NULL COMMENT '요청-응답 elapsed ms',
  `bytes_sent` bigint(20) DEFAULT NULL COMMENT 'Content-Length (가능한 경우)',
  `session_id` varchar(100) DEFAULT NULL COMMENT 'PCMS_SID 마지막 8자',
  `trace_id` varchar(64) DEFAULT NULL COMMENT 'MDC traceId',
  `logged_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_access_id`,`logged_at`),
  KEY `idx_log_access_logged_at` (`logged_at`),
  KEY `idx_log_access_site_logged` (`site_id`,`logged_at`),
  KEY `idx_log_access_uri` (`request_uri`(255),`logged_at`),
  KEY `idx_log_access_user` (`actor_user_id`,`logged_at`),
  KEY `idx_access_menu` (`menu_id`,`logged_at`)
) ENGINE=InnoDB AUTO_INCREMENT=21307 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='접근 로그 — 모든 HTTP 요청 (정적 리소스 제외)'
 PARTITION BY RANGE (unix_timestamp(`logged_at`))
(PARTITION `p_init` VALUES LESS THAN (1777561200) ENGINE = InnoDB,
 PARTITION `p_max` VALUES LESS THAN MAXVALUE ENGINE = InnoDB);

/*Table structure for table `log_audit` */

DROP TABLE IF EXISTS `log_audit`;

CREATE TABLE `log_audit` (
  `log_audit_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '감사로그 ID',
  `actor_user_id` varchar(40) DEFAULT NULL COMMENT '행위자 user_id',
  `actor_user_type` varchar(20) DEFAULT NULL COMMENT '행위자 유형',
  `actor_login_id` varchar(50) DEFAULT NULL COMMENT '행위자 로그인 ID',
  `action` varchar(30) NOT NULL COMMENT '행위 (CREATE/UPDATE/DELETE/LOGIN...)',
  `target_entity` varchar(50) DEFAULT NULL COMMENT '대상 엔티티',
  `target_id` varchar(40) DEFAULT NULL COMMENT '대상 ID',
  `request_uri` varchar(1000) DEFAULT NULL COMMENT '요청 URI',
  `http_method` varchar(10) DEFAULT NULL COMMENT 'HTTP 메서드',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '클라이언트 IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `before_json` longtext DEFAULT NULL COMMENT '변경 전 JSON' CHECK (`before_json` is null or json_valid(`before_json`)),
  `after_json` longtext DEFAULT NULL COMMENT '변경 후 JSON' CHECK (`after_json` is null or json_valid(`after_json`)),
  `result` varchar(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '결과' CHECK (`result` in ('SUCCESS','FAIL','ERROR')),
  `trace_id` varchar(50) DEFAULT NULL COMMENT '분산 추적 ID',
  `logged_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '로그 시각',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_audit_id`,`logged_at`),
  KEY `idx_audit_actor` (`actor_user_id`,`logged_at`),
  KEY `idx_audit_entity` (`target_entity`,`target_id`,`logged_at`),
  KEY `idx_audit_action` (`action`,`logged_at`)
) ENGINE=InnoDB AUTO_INCREMENT=3080 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci ROW_FORMAT=DYNAMIC COMMENT='감사 로그 (관리자 CUD 전수, 압축)' `PAGE_COMPRESSED`=1;

/*Table structure for table `log_error` */

DROP TABLE IF EXISTS `log_error`;

CREATE TABLE `log_error` (
  `log_error_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `site_id` varchar(40) DEFAULT NULL,
  `menu_id` varchar(40) DEFAULT NULL COMMENT '에러가 발생한 메뉴 ID',
  `site_code` varchar(30) DEFAULT NULL,
  `actor_user_id` varchar(40) DEFAULT NULL,
  `actor_user_type` varchar(20) DEFAULT NULL,
  `actor_login_id` varchar(50) DEFAULT NULL,
  `request_uri` varchar(500) DEFAULT NULL,
  `http_method` varchar(10) DEFAULT NULL,
  `query_string` varchar(500) DEFAULT NULL,
  `client_ip` varchar(50) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `error_class` varchar(255) NOT NULL COMMENT '예외 FQCN 예: java.lang.NullPointerException',
  `error_message` varchar(2000) DEFAULT NULL,
  `stack_trace` mediumtext DEFAULT NULL COMMENT '잘린 스택트레이스 (최대 32KB 권장)',
  `status_code` int(11) DEFAULT NULL COMMENT '응답 상태 (500 등)',
  `trace_id` varchar(64) DEFAULT NULL,
  `session_id` varchar(100) DEFAULT NULL,
  `logged_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_error_id`,`logged_at`),
  KEY `idx_log_error_logged_at` (`logged_at`),
  KEY `idx_log_error_class` (`error_class`(100),`logged_at`),
  KEY `idx_log_error_user` (`actor_user_id`,`logged_at`),
  KEY `idx_log_error_uri` (`request_uri`(255),`logged_at`),
  KEY `idx_error_menu` (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='애플리케이션 에러 로그 — 미처리 예외 자동 기록'
 PARTITION BY RANGE (unix_timestamp(`logged_at`))
(PARTITION `p_init` VALUES LESS THAN (1777561200) ENGINE = InnoDB,
 PARTITION `p_max` VALUES LESS THAN MAXVALUE ENGINE = InnoDB);

/*Table structure for table `log_file_download` */

DROP TABLE IF EXISTS `log_file_download`;

CREATE TABLE `log_file_download` (
  `log_file_download_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `file_id` varchar(40) DEFAULT NULL COMMENT 'tb_file.file_id (SINGLE/ADMIN)',
  `file_group_id` varchar(40) DEFAULT NULL COMMENT 'tb_file_group.file_group_id (GROUP_ZIP)',
  `download_type` varchar(20) NOT NULL COMMENT 'SINGLE=일반 단일, ADMIN=관리자 상태무관, GROUP_ZIP=그룹 ZIP' CHECK (`download_type` in ('SINGLE','GROUP_ZIP','ADMIN')),
  `actor_user_id` varchar(40) DEFAULT NULL COMMENT 'UUID v7 PK (SYSTEM/ANONYMOUS 포함)',
  `actor_user_type` varchar(20) DEFAULT NULL COMMENT 'MEMBER|ADMIN|ANONYMOUS',
  `actor_login_id` varchar(50) DEFAULT NULL COMMENT '로그인 ID (마스킹 없이 원본)',
  `original_name` varchar(512) DEFAULT NULL,
  `extension` varchar(20) DEFAULT NULL,
  `size_bytes` bigint(20) DEFAULT NULL COMMENT '전송된 바이트 수 (Range 시 부분)',
  `request_uri` varchar(1000) DEFAULT NULL,
  `client_ip` varchar(50) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `trace_id` varchar(50) DEFAULT NULL COMMENT 'OTel trace id',
  `result` varchar(20) NOT NULL DEFAULT 'SUCCESS' CHECK (`result` in ('SUCCESS','FAIL','BLOCKED')),
  `downloaded_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_file_download_id`,`downloaded_at`),
  KEY `idx_logfiledl_file` (`file_id`,`downloaded_at`),
  KEY `idx_logfiledl_group` (`file_group_id`,`downloaded_at`),
  KEY `idx_logfiledl_actor` (`actor_user_id`,`downloaded_at`),
  KEY `idx_logfiledl_type` (`download_type`,`downloaded_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1632 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='파일 다운로드 이력 (file_id 단위 레코드)';

/*Table structure for table `log_login` */

DROP TABLE IF EXISTS `log_login`;

CREATE TABLE `log_login` (
  `log_login_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '로그인로그 ID',
  `user_id` varchar(40) DEFAULT NULL COMMENT '사용자 ID (성공 시)',
  `user_type` varchar(20) DEFAULT NULL COMMENT '사용자 유형 (MEMBER/ADMIN)',
  `login_id` varchar(50) DEFAULT NULL COMMENT '입력 로그인 ID',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '클라이언트 IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `result` varchar(20) NOT NULL COMMENT '결과' CHECK (`result` in ('SUCCESS','FAIL','LOCKED','2FA_FAIL')),
  `fail_reason` varchar(100) DEFAULT NULL COMMENT '실패 사유',
  `session_id` varchar(100) DEFAULT NULL COMMENT '세션 ID',
  `logged_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '로그 시각 (TZ 독립, 파티션 키)',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_login_id`,`logged_at`),
  KEY `idx_log_login_user` (`user_id`,`logged_at`),
  KEY `idx_log_login_login_id` (`login_id`,`logged_at`),
  KEY `idx_log_login_ip` (`client_ip`,`logged_at`)
) ENGINE=InnoDB AUTO_INCREMENT=451 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='로그인 이력';

/*Table structure for table `log_privacy_access` */

DROP TABLE IF EXISTS `log_privacy_access`;

CREATE TABLE `log_privacy_access` (
  `log_privacy_access_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '로그 PK',
  `actor_user_id` varchar(40) NOT NULL COMMENT '접속자 UUID v7 PK',
  `actor_user_type` varchar(20) NOT NULL COMMENT 'ADMIN | MEMBER | SYSTEM',
  `actor_login_id` varchar(50) DEFAULT NULL COMMENT '접속자 사번/loginId (사람이 읽기용)',
  `client_ip` varchar(45) NOT NULL COMMENT '접속자 IP (IPv6 45자)',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `session_id` varchar(100) DEFAULT NULL COMMENT '세션 식별자 (다중세션 변별)',
  `request_uri` varchar(1000) DEFAULT NULL COMMENT '접근 URI (쿼리스트링 포함, 비-HTTP 시 NULL)',
  `http_method` varchar(10) DEFAULT NULL COMMENT 'GET | POST | ...',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '통합 viewer timeline 키 (MDC traceId)',
  `target_entity` varchar(50) NOT NULL COMMENT '대상 테이블 (tb_member/tb_admin/tb_employee/...)',
  `target_user_type` varchar(20) DEFAULT NULL COMMENT '정보주체 user_type',
  `target_id` varchar(40) DEFAULT NULL COMMENT '정보주체 단건 UUID (목록조회는 NULL)',
  `target_count` int(11) NOT NULL DEFAULT 1 COMMENT '처리 건수 (목록/엑셀 N건)',
  `pii_fields` varchar(500) DEFAULT NULL COMMENT '취급 PII 항목 CSV (name,email,phone,rrn,...)',
  `access_action` varchar(20) NOT NULL COMMENT '수행업무' CHECK (`access_action` in ('READ','SEARCH','EXPORT','PRINT','UPDATE','DELETE','DECRYPT')),
  `access_reason` varchar(2000) DEFAULT NULL COMMENT '대량조회/다운로드 사유 (소명용)',
  `result` varchar(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'PIPA 고시 §8 처리 결과' CHECK (`result` in ('SUCCESS','FAIL','DENIED','ERROR')),
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '실패/거부 사유',
  `accessed_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '접속 일시',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성일시',
  PRIMARY KEY (`log_privacy_access_id`,`accessed_at`),
  KEY `idx_privacy_actor` (`actor_user_id`,`accessed_at`),
  KEY `idx_privacy_target` (`target_entity`,`target_id`,`accessed_at`),
  KEY `idx_privacy_login_id` (`actor_login_id`,`accessed_at`),
  KEY `idx_privacy_action` (`access_action`,`accessed_at`),
  KEY `idx_privacy_trace` (`trace_id`),
  KEY `idx_privacy_result` (`result`,`accessed_at`)
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='개인정보 접근 이력 (개인정보보호법 §29, 안전성확보조치 고시 §8)';

/*Table structure for table `log_security` */

DROP TABLE IF EXISTS `log_security`;

CREATE TABLE `log_security` (
  `log_security_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '보안로그 ID',
  `event_type` varchar(50) NOT NULL COMMENT '이벤트 유형 (UPLOAD_BLOCK/LOGIN_LOCK/SSRF_BLOCK/...)',
  `severity` varchar(10) NOT NULL COMMENT '심각도' CHECK (`severity` in ('INFO','WARN','CRITICAL')),
  `actor_id` varchar(40) DEFAULT NULL COMMENT '관련 사용자 ID',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '클라이언트 IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `request_uri` varchar(1000) DEFAULT NULL COMMENT '요청 URI',
  `detail_json` longtext DEFAULT NULL COMMENT '상세 정보 JSON' CHECK (`detail_json` is null or json_valid(`detail_json`)),
  `trace_id` varchar(64) DEFAULT NULL COMMENT 'MDC traceId — 통합 viewer timeline 키',
  `logged_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '로그 시각',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_security_id`,`logged_at`),
  KEY `idx_security_event` (`event_type`,`logged_at`),
  KEY `idx_security_ip` (`client_ip`,`logged_at`),
  KEY `idx_security_actor` (`actor_id`,`logged_at`),
  KEY `idx_security_trace` (`trace_id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='보안 이벤트 로그';

/*Table structure for table `shedlock` */

DROP TABLE IF EXISTS `shedlock`;

CREATE TABLE `shedlock` (
  `NAME` varchar(64) NOT NULL,
  `lock_until` timestamp(3) NOT NULL,
  `locked_at` timestamp(3) NOT NULL DEFAULT current_timestamp(3),
  `locked_by` varchar(255) NOT NULL,
  PRIMARY KEY (`NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='ShedLock — 분산 cron 락 (gopcms500 + clamav-daemon 공용)';

/*Table structure for table `stat_access_daily` */

DROP TABLE IF EXISTS `stat_access_daily`;

CREATE TABLE `stat_access_daily` (
  `stat_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL COMMENT '집계 일자 (어제 기준)',
  `site_id` varchar(40) DEFAULT NULL,
  `site_code` varchar(30) DEFAULT NULL,
  `user_type` varchar(20) DEFAULT NULL COMMENT 'ANONYMOUS|MEMBER|ADMIN — NULL=전체',
  `bucket_hour` tinyint(4) DEFAULT NULL COMMENT '0~23 — NULL=하루 전체',
  `pv` bigint(20) NOT NULL DEFAULT 0 COMMENT 'page view (요청 수)',
  `uv` bigint(20) NOT NULL DEFAULT 0 COMMENT 'unique visitor (DISTINCT client_ip)',
  `unique_users` bigint(20) NOT NULL DEFAULT 0 COMMENT 'DISTINCT actor_user_id (로그인만)',
  `avg_response_ms` int(11) NOT NULL DEFAULT 0,
  `p95_response_ms` int(11) NOT NULL DEFAULT 0,
  `err_4xx` bigint(20) NOT NULL DEFAULT 0,
  `err_5xx` bigint(20) NOT NULL DEFAULT 0,
  `bytes_sent` bigint(20) NOT NULL DEFAULT 0,
  `computed_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`stat_id`),
  UNIQUE KEY `uk_stat_access_daily` (`stat_date`,`site_id`,`user_type`,`bucket_hour`),
  KEY `idx_stat_access_date` (`stat_date`),
  KEY `idx_stat_access_site_date` (`site_id`,`stat_date`)
) ENGINE=InnoDB AUTO_INCREMENT=12500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='접근 로그 일별 집계 (배치 04:00)';

/*Table structure for table `stat_access_uri_daily` */

DROP TABLE IF EXISTS `stat_access_uri_daily`;

CREATE TABLE `stat_access_uri_daily` (
  `stat_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `site_id` varchar(40) DEFAULT NULL,
  `site_code` varchar(30) DEFAULT NULL,
  `request_uri` varchar(500) NOT NULL,
  `pv` bigint(20) NOT NULL DEFAULT 0,
  `uv` bigint(20) NOT NULL DEFAULT 0,
  `avg_response_ms` int(11) NOT NULL DEFAULT 0,
  `computed_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`stat_id`),
  UNIQUE KEY `uk_stat_access_uri_daily` (`stat_date`,`site_id`,`request_uri`),
  KEY `idx_stat_access_uri_date` (`stat_date`,`pv` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=45365 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='URI 별 일별 집계 — 인기 페이지 랭킹용';

/*Table structure for table `stat_content_view` */

DROP TABLE IF EXISTS `stat_content_view`;

CREATE TABLE `stat_content_view` (
  `stat_date` date NOT NULL COMMENT '통계 일자',
  `content_id` varchar(40) NOT NULL COMMENT '콘텐츠 ID',
  `view_count` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '조회수',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`stat_date`,`content_id`),
  KEY `idx_stat_content_view_c` (`content_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='콘텐츠 일별 조회수';

/*Table structure for table `stat_daily_visit` */

DROP TABLE IF EXISTS `stat_daily_visit`;

CREATE TABLE `stat_daily_visit` (
  `stat_date` date NOT NULL COMMENT '통계 일자',
  `site_id` varchar(40) NOT NULL COMMENT '사이트 ID',
  `visit_count` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '방문수',
  `unique_count` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '순방문자수',
  `pv_count` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '페이지뷰',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`stat_date`,`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='일별 방문 통계';

/* 2026-07-31 삭제 — stat_search_keyword. 검색을 외부 검색엔진(contextPath=/search)으로
   분리해 앱이 검색어를 수집하지 않는다(D10). 검색어 통계는 검색엔진 쪽에서 낸다. */
