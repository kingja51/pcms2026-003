

USE `pcms2026-003-primary`;

/*Table structure for table `tb_admin` */

DROP TABLE IF EXISTS `tb_admin`;

CREATE TABLE `tb_admin` (
  `admin_id` varchar(40) NOT NULL COMMENT '관리자 ID (UUID v7)',
  `admin_seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '관리자 일련번호 (VIEW에서 uniq_id)',
  `admin_group_id` varchar(40) NOT NULL COMMENT '관리자그룹 ID',
  `department_id` varchar(40) DEFAULT NULL,
  `login_id` varchar(50) NOT NULL COMMENT '로그인 ID',
  `PASSWORD` varchar(100) NOT NULL COMMENT '비밀번호 (BCrypt)',
  `password_changed_at` datetime NOT NULL COMMENT '비밀번호 변경 일시',
  `password_expire_at` datetime DEFAULT NULL COMMENT '비밀번호 만료 일시',
  `role_ids` text DEFAULT NULL COMMENT '역할 ID CSV (계층 확장 denormalized)',
  `role_codes` text DEFAULT NULL COMMENT 'ROLE 코드',
  `group_ids` text DEFAULT NULL COMMENT '그룹 ID CSV (복수 그룹)',
  `admin_name` varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 관리자 이름',
  `email` varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 이메일',
  `email_hash` char(64) NOT NULL COMMENT 'HMAC-SHA256(email) 검색/중복확인용',
  `phone` varchar(512) DEFAULT NULL COMMENT '{AG} AES-256-GCM 전화번호',
  `department_name` varchar(100) DEFAULT NULL COMMENT '부서',
  `two_factor_enabled_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '2FA 활성화 여부' CHECK (`two_factor_enabled_yn` in ('Y','N')),
  `two_factor_secret` varchar(512) DEFAULT NULL COMMENT '{AG} TOTP Secret',
  `ip_whitelist` varchar(2000) DEFAULT NULL COMMENT '개인 IP 화이트리스트 (그룹 오버라이드)',
  `allowed_time_from` time DEFAULT NULL COMMENT '로그인 허용 시작 시각',
  `allowed_time_to` time DEFAULT NULL COMMENT '로그인 허용 종료 시각',
  `STATUS` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태' CHECK (`STATUS` in ('ACTIVE','LOCKED','INACTIVE','SUSPENDED')),
  `login_fail_count` int(11) NOT NULL DEFAULT 0 COMMENT '로그인 실패 횟수' CHECK (`login_fail_count` >= 0),
  `locked_until` datetime DEFAULT NULL COMMENT '잠금 해제 예정 일시',
  `captcha_required_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '5회 실패 잠금 해제 후 CAPTCHA 강제 여부 — 다음 성공 시 자동 N',
  `last_login_at` datetime DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '최종 로그인 IP',
  `last_access_at` datetime DEFAULT NULL COMMENT '최종 접속 일시',
  `remarks` varchar(1000) DEFAULT NULL COMMENT '비고',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`admin_id`),
  UNIQUE KEY `uk_admin_seq` (`admin_seq`),
  UNIQUE KEY `uk_admin_login` (`login_id`),
  KEY `idx_admin_group` (`admin_group_id`),
  KEY `idx_admin_email_hash` (`email_hash`),
  KEY `idx_admin_status` (`STATUS`,`delete_yn`),
  KEY `idx_admin_dept` (`department_id`),
  CONSTRAINT `fk_admin_dept` FOREIGN KEY (`department_id`) REFERENCES `tb_department` (`department_id`),
  CONSTRAINT `fk_admin_group` FOREIGN KEY (`admin_group_id`) REFERENCES `tb_admin_group` (`admin_group_id`),
  CONSTRAINT `chk_admin_captcha_required_yn` CHECK (`captcha_required_yn` in ('Y','N'))
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자';

/*Table structure for table `tb_admin_allow_ip` */

DROP TABLE IF EXISTS `tb_admin_allow_ip`;

CREATE TABLE `tb_admin_allow_ip` (
  `ip_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `admin_id` varchar(40) NOT NULL COMMENT '관리자 UUID (FK)',
  `ip_address` varchar(50) NOT NULL COMMENT 'IP 주소 (SINGLE) / CIDR (CIDR)',
  `ip_type` varchar(20) NOT NULL DEFAULT 'SINGLE' COMMENT 'SINGLE / RANGE / CIDR' CHECK (`ip_type` in ('SINGLE','RANGE','CIDR')),
  `ip_start` varchar(50) DEFAULT NULL COMMENT 'IP 범위 시작 (RANGE)',
  `ip_end` varchar(50) DEFAULT NULL COMMENT 'IP 범위 종료 (RANGE)',
  `description` varchar(200) DEFAULT NULL COMMENT '설명 (예: 본사 사무실)',
  `access_count` int(11) NOT NULL DEFAULT 0 COMMENT '접근 횟수 (성공시 +1)',
  `last_access_at` datetime DEFAULT NULL COMMENT '마지막 접근 일시',
  `expires_at` datetime DEFAULT NULL COMMENT '만료 일시 (NULL=영구)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정일시',
  PRIMARY KEY (`ip_id`),
  KEY `idx_admin_allow_ip_admin` (`admin_id`,`delete_yn`,`use_yn`),
  KEY `idx_admin_allow_ip_address` (`ip_address`),
  KEY `idx_admin_allow_ip_expires` (`expires_at`),
  CONSTRAINT `fk_admin_allow_ip_admin` FOREIGN KEY (`admin_id`) REFERENCES `tb_admin` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자 접속 허용 IP 테이블 (whitelist if defined)';

/*Table structure for table `tb_admin_group` */

DROP TABLE IF EXISTS `tb_admin_group`;

CREATE TABLE `tb_admin_group` (
  `admin_group_id` varchar(40) NOT NULL COMMENT '관리자그룹 ID (UUID v7)',
  `group_code` varchar(30) NOT NULL COMMENT '그룹 코드',
  `group_name` varchar(100) NOT NULL COMMENT '그룹 명',
  `DESCRIPTION` varchar(500) DEFAULT NULL COMMENT '설명',
  `default_role_id` varchar(40) DEFAULT NULL,
  `ip_whitelist` varchar(2000) DEFAULT NULL COMMENT '허용 IP CIDR CSV',
  `allowed_time_from` time DEFAULT NULL COMMENT '허용 시작 시각',
  `allowed_time_to` time DEFAULT NULL COMMENT '허용 종료 시각',
  `two_factor_required` char(1) NOT NULL DEFAULT 'Y' COMMENT '2FA 강제 여부' CHECK (`two_factor_required` in ('Y','N')),
  `password_policy_id` varchar(40) DEFAULT NULL,
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`admin_group_id`),
  UNIQUE KEY `uk_admin_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자 그룹';

/*Table structure for table `tb_admin_password_history` */

DROP TABLE IF EXISTS `tb_admin_password_history`;

CREATE TABLE `tb_admin_password_history` (
  `pwd_history_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `admin_id` varchar(40) DEFAULT NULL,
  `password_hash` varchar(100) NOT NULL COMMENT '비밀번호 해시 (BCrypt)',
  `changed_at` datetime NOT NULL COMMENT '변경 일시',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`pwd_history_id`),
  KEY `idx_admin_pwd_hst` (`admin_id`,`changed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자 비밀번호 이력 (재사용 방지)';

/*Table structure for table `tb_admin_role` */

DROP TABLE IF EXISTS `tb_admin_role`;

CREATE TABLE `tb_admin_role` (
  `admin_role_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `admin_id` varchar(40) NOT NULL COMMENT '관리자 ID',
  `role_id` varchar(40) NOT NULL COMMENT '역할 ID',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`admin_role_id`),
  UNIQUE KEY `uk_admin_role` (`admin_id`,`role_id`),
  KEY `idx_admin_role_role` (`role_id`),
  CONSTRAINT `fk_admin_role_admin` FOREIGN KEY (`admin_id`) REFERENCES `tb_admin` (`admin_id`),
  CONSTRAINT `fk_admin_role_role` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자-역할 매핑';

/*Table structure for table `tb_admin_withdraw` */

DROP TABLE IF EXISTS `tb_admin_withdraw`;

CREATE TABLE `tb_admin_withdraw` (
  `admin_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `admin_seq` bigint(20) NOT NULL COMMENT '원 관리자 일련번호',
  `admin_group_id` varchar(40) DEFAULT NULL,
  `login_id` varchar(50) NOT NULL COMMENT '로그인 ID (감사 추적용 평문 유지)',
  `email_hash` char(64) NOT NULL COMMENT '이메일 해시',
  `department_name` varchar(100) DEFAULT NULL COMMENT '부서',
  `withdraw_at` datetime NOT NULL COMMENT '탈퇴 처리 일시',
  `withdraw_reason` varchar(50) NOT NULL COMMENT '탈퇴 사유 (RESIGN/TRANSFER/FORCE)',
  `last_login_at` datetime DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '최종 로그인 IP',
  `retention_expire_at` datetime NOT NULL COMMENT '감사 보관 만료 일시 (최소 3년 권고)',
  `withdrawn_by` varchar(40) DEFAULT NULL,
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  `department_id` varchar(40) DEFAULT NULL COMMENT '부서ID',
  PRIMARY KEY (`admin_id`),
  KEY `idx_admin_withdraw_seq` (`admin_seq`),
  KEY `idx_admin_withdraw_login` (`login_id`),
  KEY `idx_admin_withdraw_expire` (`retention_expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='탈퇴 관리자 (감사 보관)';

/*Table structure for table `tb_api_weather` */

DROP TABLE IF EXISTS `tb_api_weather`;

CREATE TABLE `tb_api_weather` (
  `weather_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '일련번호',
  `base_date` varchar(8) NOT NULL COMMENT '발표 일자 yyyyMMdd',
  `base_time` varchar(4) NOT NULL COMMENT '발표 시각 HHmm (0200/0500/0800/1100/1400/1700/2000/2300)',
  `category` varchar(8) NOT NULL COMMENT 'TMP/POP/PTY/SKY/REH/WSD/VEC/TMN/TMX/PCP/SNO/...',
  `fcst_date` varchar(8) NOT NULL COMMENT '예보 일자 yyyyMMdd',
  `fcst_time` varchar(4) NOT NULL COMMENT '예보 시각 HHmm',
  `fcst_value` varchar(50) NOT NULL COMMENT '예보 값 (단위는 category 별)',
  `nx` int(11) NOT NULL COMMENT 'KMA 격자 X',
  `ny` int(11) NOT NULL COMMENT 'KMA 격자 Y',
  `created_by` varchar(40) DEFAULT 'SCHEDULER' COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`weather_id`),
  UNIQUE KEY `uk_api_weather` (`base_date`,`base_time`,`category`,`fcst_date`,`fcst_time`,`nx`,`ny`),
  KEY `idx_api_weather_grid` (`nx`,`ny`,`fcst_date`,`fcst_time`),
  KEY `idx_api_weather_base` (`base_date`,`base_time`)
) ENGINE=InnoDB AUTO_INCREMENT=101444 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='기상청 단기예보 (VilageFcstInfoService_2.0) 적재';

/*Table structure for table `tb_approval` */

DROP TABLE IF EXISTS `tb_approval`;

CREATE TABLE `tb_approval` (
  `approval_id` varchar(40) NOT NULL COMMENT '결재 ID (UUID v7)',
  `target_entity` varchar(50) NOT NULL COMMENT '대상 엔티티 타입 (CONTENT/BBS_ARTICLE/...)',
  `target_id` varchar(40) DEFAULT NULL,
  `approval_title` varchar(300) NOT NULL COMMENT '결재 제목',
  `approval_content` text DEFAULT NULL COMMENT '결재 내용',
  `requester_user_id` varchar(40) DEFAULT NULL,
  `current_step` int(11) NOT NULL DEFAULT 1 COMMENT '현재 단계',
  `total_step` int(11) NOT NULL DEFAULT 1 COMMENT '총 단계',
  `STATUS` varchar(20) NOT NULL DEFAULT 'REQUESTED' COMMENT '상태' CHECK (`STATUS` in ('REQUESTED','IN_PROGRESS','APPROVED','REJECTED','CANCELED')),
  `requested_at` datetime NOT NULL COMMENT '상신 일시',
  `completed_at` datetime DEFAULT NULL COMMENT '완료 일시',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`approval_id`),
  KEY `idx_approval_target` (`target_entity`,`target_id`),
  KEY `idx_approval_req` (`requester_user_id`,`STATUS`),
  KEY `idx_approval_status` (`STATUS`,`requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='결재건';

/*Table structure for table `tb_approval_line` */

DROP TABLE IF EXISTS `tb_approval_line`;

CREATE TABLE `tb_approval_line` (
  `approval_line_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `approval_id` varchar(40) NOT NULL COMMENT '결재 ID',
  `step_no` int(11) NOT NULL COMMENT '단계 번호',
  `approver_user_id` varchar(40) DEFAULT NULL,
  `approval_type` varchar(20) NOT NULL DEFAULT 'APPROVE' COMMENT '결재 유형' CHECK (`approval_type` in ('APPROVE','REVIEW','AGREEMENT')),
  `STATUS` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태' CHECK (`STATUS` in ('PENDING','APPROVED','REJECTED','SKIPPED')),
  `processed_at` datetime DEFAULT NULL COMMENT '처리 일시',
  `COMMENT` varchar(1000) DEFAULT NULL COMMENT '의견',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`approval_line_id`),
  UNIQUE KEY `uk_approval_line` (`approval_id`,`step_no`),
  KEY `idx_approval_line_user` (`approver_user_id`,`STATUS`),
  CONSTRAINT `fk_approval_line` FOREIGN KEY (`approval_id`) REFERENCES `tb_approval` (`approval_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='결재선';

/*Table structure for table `tb_auth` */

DROP TABLE IF EXISTS `tb_auth`;

CREATE TABLE `tb_auth` (
  `auth_id` varchar(40) NOT NULL COMMENT '권한 ID (UUID v7)',
  `auth_group` varchar(30) NOT NULL COMMENT '권한 그룹 (CONTENT/BOARD/MEMBER/SYSTEM...)',
  `auth_code` varchar(50) NOT NULL COMMENT '권한 코드 (예: CONTENT_WRITE)',
  `auth_name` varchar(100) NOT NULL COMMENT '권한 명',
  `DESCRIPTION` varchar(500) DEFAULT NULL COMMENT '설명',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`auth_id`),
  UNIQUE KEY `uk_auth_code` (`auth_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='세밀 권한 (버튼/행단위 보조)';

/*Table structure for table `tb_banner` */

DROP TABLE IF EXISTS `tb_banner`;

CREATE TABLE `tb_banner` (
  `banner_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `site_id` varchar(40) NOT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `banner_title` varchar(200) NOT NULL COMMENT '배너 제목',
  `banner_location` varchar(50) NOT NULL COMMENT '배너 노출 위치',
  `file_group_id` varchar(40) DEFAULT NULL COMMENT 'file-picker 의 (entityType=BANNER, entityId=bannerId) 그룹 ID. 그룹의 첫 이미지가 노출용',
  `alt_text` varchar(500) DEFAULT NULL COMMENT '대체 텍스트 (접근성)',
  `link_url` varchar(1000) DEFAULT NULL COMMENT '링크 URL',
  `link_target` varchar(10) NOT NULL DEFAULT '_self' COMMENT '링크 타겟' CHECK (`link_target` in ('_self','_blank')),
  `show_from` datetime NOT NULL COMMENT '노출 시작 일시',
  `show_to` datetime NOT NULL COMMENT '노출 종료 일시',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`banner_id`),
  KEY `idx_banner_show` (`site_id`,`banner_location`,`use_yn`,`show_from`,`show_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='배너';

/*Table structure for table `tb_bbs_article` */

DROP TABLE IF EXISTS `tb_bbs_article`;

CREATE TABLE `tb_bbs_article` (
  `article_id` varchar(40) NOT NULL COMMENT 'UUID v7',
  `bbs_master_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `category_id` varchar(40) DEFAULT NULL,
  `file_group_id` varchar(40) NOT NULL COMMENT '파일그룹 ID (UUID v7, FK tb_file_group)',
  `writer_user_id` varchar(40) DEFAULT NULL,
  `writer_user_type` varchar(20) DEFAULT NULL CHECK (`writer_user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF','GUEST')),
  `writer_name` varchar(100) NOT NULL,
  `writer_password` varchar(100) DEFAULT NULL COMMENT '비로그인 게시글 BCrypt(12)',
  `title` varchar(300) NOT NULL,
  `content` mediumtext NOT NULL,
  `press_name` varchar(100) DEFAULT NULL COMMENT '언론사명',
  `link_url` varchar(500) DEFAULT NULL COMMENT '링크 URL',
  `published_at` date DEFAULT NULL COMMENT '공지글 게시 일자',
  `notice_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`notice_yn` in ('Y','N')),
  `secret_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`secret_yn` in ('Y','N')),
  `view_count` bigint(20) unsigned NOT NULL DEFAULT 0,
  `like_count` bigint(20) unsigned NOT NULL DEFAULT 0,
  `report_count` int(11) NOT NULL DEFAULT 0,
  `comment_count` int(11) NOT NULL DEFAULT 0,
  `client_ip` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (`status` in ('PUBLISHED','HIDDEN','REPORTED','DELETED')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`article_id`),
  KEY `idx_article_bbs_status` (`bbs_master_id`,`status`,`notice_yn`,`created_at`),
  KEY `idx_article_writer` (`writer_user_id`),
  KEY `idx_article_file_group` (`file_group_id`),
  FULLTEXT KEY `ft_article` (`title`,`content`),
  CONSTRAINT `fk_article_bbs` FOREIGN KEY (`bbs_master_id`) REFERENCES `tb_bbs_master` (`bbs_master_id`),
  CONSTRAINT `fk_article_file_group` FOREIGN KEY (`file_group_id`) REFERENCES `tb_file_group` (`file_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시글';

/*Table structure for table `tb_bbs_category` */

DROP TABLE IF EXISTS `tb_bbs_category`;

CREATE TABLE `tb_bbs_category` (
  `category_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `bbs_master_id` varchar(40) NOT NULL COMMENT '게시판마스터 ID',
  `category_code` varchar(50) NOT NULL COMMENT '카테고리 코드',
  `category_name` varchar(100) NOT NULL COMMENT '카테고리 명',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_bbs_category` (`bbs_master_id`,`category_code`),
  CONSTRAINT `fk_bbs_cat` FOREIGN KEY (`bbs_master_id`) REFERENCES `tb_bbs_master` (`bbs_master_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시판 카테고리';

/*Table structure for table `tb_bbs_comment` */

DROP TABLE IF EXISTS `tb_bbs_comment`;

CREATE TABLE `tb_bbs_comment` (
  `comment_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `article_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `parent_comment_id` varchar(40) DEFAULT NULL,
  `writer_user_id` varchar(40) DEFAULT NULL,
  `writer_user_type` varchar(20) DEFAULT NULL CHECK (`writer_user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF','GUEST')),
  `writer_name` varchar(100) NOT NULL,
  `writer_password` varchar(100) DEFAULT NULL,
  `content` text NOT NULL,
  `like_count` bigint(20) unsigned NOT NULL DEFAULT 0,
  `report_count` int(11) NOT NULL DEFAULT 0,
  `secret_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '비밀 댓글 — 작성자/글 주인/관리자만 본문 조회',
  `depth` int(11) NOT NULL DEFAULT 1,
  `client_ip` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (`status` in ('PUBLISHED','HIDDEN','REPORTED','DELETED')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`comment_id`),
  KEY `idx_comment_article` (`article_id`,`parent_comment_id`,`created_at`),
  CONSTRAINT `fk_comment_article` FOREIGN KEY (`article_id`) REFERENCES `tb_bbs_article` (`article_id`),
  CONSTRAINT `chk_bbs_comment_secret_yn` CHECK (`secret_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시글 댓글';

/*Table structure for table `tb_bbs_like` */

DROP TABLE IF EXISTS `tb_bbs_like`;

CREATE TABLE `tb_bbs_like` (
  `like_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `target_type` varchar(20) NOT NULL,
  `target_id` varchar(40) DEFAULT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `user_type` varchar(20) NOT NULL CHECK (`user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF')),
  `source_url` varchar(1000) DEFAULT NULL COMMENT '좋아요가 클릭된 페이지의 URL 경로 (예: /bbs/airbnb/free/123 또는 /airbnb/host-guide)',
  `menu_id` varchar(40) DEFAULT NULL,
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`like_id`),
  UNIQUE KEY `uk_like_target_user` (`target_type`,`target_id`,`user_id`),
  KEY `idx_like_target` (`target_type`,`target_id`),
  KEY `idx_like_user` (`user_id`,`target_type`),
  KEY `idx_bbs_like_menu` (`menu_id`),
  CONSTRAINT `chk_bbs_like_target_type` CHECK (`target_type` in ('ARTICLE','COMMENT','CONTENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시판 좋아요 — article/comment 통합';

/*Table structure for table `tb_bbs_master` */

DROP TABLE IF EXISTS `tb_bbs_master`;

CREATE TABLE `tb_bbs_master` (
  `bbs_master_id` varchar(40) NOT NULL COMMENT 'UUID v7',
  `site_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `menu_id` varchar(40) DEFAULT NULL,
  `bbs_code` varchar(50) NOT NULL COMMENT '사이트 내 식별 코드 (영문/숫자/언더스코어)',
  `bbs_name` varchar(100) NOT NULL,
  `bbs_type` varchar(20) NOT NULL,
  `comment_yn` char(1) NOT NULL DEFAULT 'Y' CHECK (`comment_yn` in ('Y','N')),
  `file_yn` char(1) NOT NULL DEFAULT 'Y' CHECK (`file_yn` in ('Y','N')),
  `file_count_max` int(11) NOT NULL DEFAULT 5,
  `file_size_max` bigint(20) NOT NULL DEFAULT 10485760 COMMENT '바이트, 기본 10MB',
  `anonymous_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`anonymous_yn` in ('Y','N')),
  `notice_top_yn` char(1) NOT NULL DEFAULT 'Y' CHECK (`notice_top_yn` in ('Y','N')),
  `html_yn` char(1) NOT NULL DEFAULT 'N' COMMENT 'content HTML 사용 여부 (Y=화이트리스트 sanitize 후 utext / N=평문 text)',
  `captcha_yn` char(1) NOT NULL DEFAULT 'N' COMMENT 'captcha 사용' CHECK (`captcha_yn` in ('Y','N')),
  `read_auth` varchar(50) NOT NULL DEFAULT 'ALL' COMMENT 'ALL | MEMBER | EMPLOYEE | ADMIN — 향후 RBAC 확장',
  `download_auth` varchar(20) NOT NULL DEFAULT 'ROLE_MEMBER' COMMENT '첨부 다운로드 권한 — ANONYMOUS|ROLE_MEMBER|ROLE_EMPLOYEE|ROLE_STAFF|OWNER_PRIVACY|ROLE_MANAGER|ROLE_ADMIN',
  `write_auth` varchar(50) NOT NULL DEFAULT 'MEMBER' COMMENT 'GUEST | MEMBER | EMPLOYEE | ADMIN',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' CHECK (`use_yn` in ('Y','N')),
  `description` varchar(500) DEFAULT NULL COMMENT '관리자 메모',
  `grouped_board_ids` varchar(1000) DEFAULT NULL COMMENT '통합 게시판 모드. 구분자 콤마,NULL = 일반 게시판',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`bbs_master_id`),
  UNIQUE KEY `uk_bbs_code` (`site_id`,`bbs_code`),
  KEY `idx_bbs_master_site_use` (`site_id`,`use_yn`,`delete_yn`),
  KEY `idx_bbs_master_menu` (`menu_id`),
  CONSTRAINT `chk_bbs_master_download_auth` CHECK (`download_auth` in ('ANONYMOUS','ROLE_MEMBER','ROLE_EMPLOYEE','ROLE_STAFF','OWNER_PRIVACY','ROLE_MANAGER','ROLE_ADMIN')),
  CONSTRAINT `chk_bbs_master_html_yn` CHECK (`html_yn` in ('Y','N')),
  CONSTRAINT `chk_bbs_master_captcha_yn` CHECK (`captcha_yn` in ('Y','N')),
  CONSTRAINT `chk_bbs_master_bbs_type` CHECK (`bbs_type` in ('NOTICE','BODO','FREE','FAQ','QNA','GALLERY','FILE','PDF','YOUTUBE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시판 마스터';

/*Table structure for table `tb_bbs_report` */

DROP TABLE IF EXISTS `tb_bbs_report`;

CREATE TABLE `tb_bbs_report` (
  `report_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `target_type` varchar(20) NOT NULL,
  `target_id` varchar(40) DEFAULT NULL,
  `reporter_user_id` varchar(40) DEFAULT NULL,
  `reporter_user_type` varchar(20) NOT NULL CHECK (`reporter_user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF')),
  `source_url` varchar(1000) DEFAULT NULL COMMENT '신고가 접수된 페이지의 URL 경로',
  `menu_id` varchar(40) DEFAULT NULL,
  `reason_code` varchar(30) NOT NULL CHECK (`reason_code` in ('SPAM','OFFENSIVE','ILLEGAL','COPYRIGHT','PRIVACY','OTHER')),
  `reason_text` varchar(1000) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (`status` in ('PENDING','REVIEWED','REJECTED')),
  `reviewed_by` varchar(40) DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `review_note` varchar(1000) DEFAULT NULL,
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_report_target_reporter` (`target_type`,`target_id`,`reporter_user_id`),
  KEY `idx_report_target` (`target_type`,`target_id`),
  KEY `idx_report_status` (`status`,`created_at`),
  KEY `idx_report_reporter` (`reporter_user_id`),
  KEY `idx_bbs_report_menu` (`menu_id`),
  CONSTRAINT `chk_bbs_report_target_type` CHECK (`target_type` in ('ARTICLE','COMMENT','CONTENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시판 신고 — article/comment 통합';

/*Table structure for table `tb_code` */

DROP TABLE IF EXISTS `tb_code`;

CREATE TABLE `tb_code` (
  `code_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `code_group_id` varchar(40) NOT NULL COMMENT '코드그룹 ID',
  `CODE` varchar(50) NOT NULL COMMENT '코드값',
  `code_name` varchar(100) NOT NULL COMMENT '코드명',
  `code_value` varchar(500) DEFAULT NULL COMMENT '부가 값',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`code_id`),
  UNIQUE KEY `uk_code_group_code` (`code_group_id`,`CODE`),
  KEY `idx_code_sort` (`code_group_id`,`sort_order`),
  CONSTRAINT `fk_code_group` FOREIGN KEY (`code_group_id`) REFERENCES `tb_code_group` (`code_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='공통코드';

/*Table structure for table `tb_code_group` */

DROP TABLE IF EXISTS `tb_code_group`;

CREATE TABLE `tb_code_group` (
  `code_group_id` varchar(40) NOT NULL COMMENT '코드그룹 ID (UUID v7)',
  `group_code` varchar(50) NOT NULL COMMENT '그룹 코드',
  `group_name` varchar(100) NOT NULL COMMENT '그룹 명',
  `DESCRIPTION` varchar(500) DEFAULT NULL COMMENT '설명',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`code_group_id`),
  UNIQUE KEY `uk_code_group` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='공통코드 그룹';

/*Table structure for table `tb_complaint_answer` */

DROP TABLE IF EXISTS `tb_complaint_answer`;

CREATE TABLE `tb_complaint_answer` (
  `answer_id` varchar(40) NOT NULL COMMENT '답변 ID (UUID v7)',
  `article_id` varchar(40) NOT NULL COMMENT '민원 게시글 FK',
  `answerer_id` varchar(40) NOT NULL COMMENT '답변자 user_id (tb_admin 또는 tb_employee)',
  `answerer_type` varchar(10) NOT NULL COMMENT '답변자 유형' CHECK (`answerer_type` in ('ADMIN','EMPLOYEE','STAFF')),
  `answerer_name` varchar(100) NOT NULL COMMENT '답변자 표시 이름',
  `answerer_dept` varchar(100) DEFAULT NULL COMMENT '부서명 (직원인 경우 선택)',
  `content` mediumtext NOT NULL COMMENT '답변 내용 (XSS sanitize 후 저장)',
  `file_group_id` varchar(40) DEFAULT NULL COMMENT '답변 첨부파일 그룹',
  `is_final` char(1) NOT NULL DEFAULT 'N' COMMENT 'Y: 최종 답변 → article.status=ANSWERED 자동 전환' CHECK (`is_final` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`answer_id`),
  KEY `idx_cmp_ans_article` (`article_id`,`created_at`),
  KEY `idx_cmp_ans_answerer` (`answerer_id`),
  KEY `fk_cmp_ans_file_grp` (`file_group_id`),
  CONSTRAINT `fk_cmp_ans_article` FOREIGN KEY (`article_id`) REFERENCES `tb_complaint_article` (`article_id`),
  CONSTRAINT `fk_cmp_ans_file_grp` FOREIGN KEY (`file_group_id`) REFERENCES `tb_file_group` (`file_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='민원 관리자 답변 — 작성자 본인+STAFF 열람';

/*Table structure for table `tb_complaint_article` */

DROP TABLE IF EXISTS `tb_complaint_article`;

CREATE TABLE `tb_complaint_article` (
  `article_id` varchar(40) NOT NULL COMMENT '민원 게시글 ID (UUID v7)',
  `complaint_master_id` varchar(40) NOT NULL COMMENT '소속 민원 마스터',
  `category_id` varchar(40) DEFAULT NULL COMMENT '카테고리 (선택)',
  `file_group_id` varchar(40) DEFAULT NULL COMMENT '첨부파일 그룹 (tb_file_group FK)',
  `writer_auth_type` varchar(10) NOT NULL COMMENT '인증 경로: MEMBER|OAUTH2|NICE' CHECK (`writer_auth_type` in ('MEMBER','OAUTH2','NICE')),
  `writer_member_id` varchar(40) DEFAULT NULL COMMENT 'tb_member.member_id (MEMBER 인증 시)',
  `writer_di_hash` char(64) DEFAULT NULL COMMENT 'HMAC-SHA256(DI) — NICE 인증 또는 DI 보유 회원',
  `writer_oauth_provider` varchar(10) DEFAULT NULL COMMENT 'OAuth2 provider (OAUTH2 인증 시)' CHECK (`writer_oauth_provider` is null or `writer_oauth_provider` in ('NAVER','KAKAO','GOOGLE')),
  `writer_oauth_uid_hash` char(64) DEFAULT NULL COMMENT 'HMAC-SHA256(provider_user_id) — OAUTH2 인증 시',
  `writer_name` varchar(100) NOT NULL COMMENT '표시 이름 (마스킹 적용, 예: 홍**)',
  `writer_contact_enc` varchar(512) DEFAULT NULL COMMENT 'AES-GCM 암호화 연락처 ({AG}prefix) — 선택 입력',
  `complaint_no` varchar(30) NOT NULL COMMENT '접수번호 (예: CPL-20260515-000001). 앱에서 채번',
  `title` varchar(300) NOT NULL COMMENT '민원 제목',
  `content` mediumtext NOT NULL COMMENT '민원 내용 (XSS sanitize 후 저장)',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '작성자 IP',
  `status` varchar(15) NOT NULL DEFAULT 'RECEIVED' COMMENT '접수|처리중|답변완료|종결|반려' CHECK (`status` in ('RECEIVED','IN_PROGRESS','ANSWERED','CLOSED','REJECTED')),
  `answer_due_at` datetime DEFAULT NULL COMMENT '답변 기한 (master.answer_deadline_days 기준 자동 산출)',
  `answered_at` datetime DEFAULT NULL COMMENT '최초 is_final=Y 답변 등록 일시',
  `closed_at` datetime DEFAULT NULL COMMENT '민원 종결 일시',
  `answer_count` int(11) NOT NULL DEFAULT 0 COMMENT '답변 건수 (삭제 제외)',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`article_id`),
  UNIQUE KEY `uk_complaint_no` (`complaint_master_id`,`complaint_no`),
  KEY `idx_cmp_art_master_status` (`complaint_master_id`,`status`,`created_at`),
  KEY `idx_cmp_art_category` (`category_id`,`status`,`created_at`),
  KEY `idx_cmp_art_member` (`writer_member_id`),
  KEY `idx_cmp_art_di_hash` (`writer_di_hash`),
  KEY `idx_cmp_art_oauth` (`writer_oauth_provider`,`writer_oauth_uid_hash`),
  KEY `idx_cmp_art_due` (`answer_due_at`,`status`),
  KEY `fk_cmp_art_file_grp` (`file_group_id`),
  CONSTRAINT `fk_cmp_art_category` FOREIGN KEY (`category_id`) REFERENCES `tb_complaint_category` (`category_id`),
  CONSTRAINT `fk_cmp_art_file_grp` FOREIGN KEY (`file_group_id`) REFERENCES `tb_file_group` (`file_group_id`),
  CONSTRAINT `fk_cmp_art_master` FOREIGN KEY (`complaint_master_id`) REFERENCES `tb_complaint_master` (`complaint_master_id`),
  CONSTRAINT `chk_cmp_art_identity` CHECK (`writer_auth_type` = 'MEMBER' and `writer_member_id` is not null or `writer_auth_type` = 'OAUTH2' and `writer_oauth_provider` is not null and `writer_oauth_uid_hash` is not null or `writer_auth_type` = 'NICE' and `writer_di_hash` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='민원(이의제기) 게시글 — 전체 비공개. 작성자(키 일치)+STAFF 열람';

/*Table structure for table `tb_complaint_category` */

DROP TABLE IF EXISTS `tb_complaint_category`;

CREATE TABLE `tb_complaint_category` (
  `category_id` varchar(40) NOT NULL COMMENT '카테고리 ID (UUID v7)',
  `complaint_master_id` varchar(40) NOT NULL COMMENT '소속 민원 마스터',
  `category_code` varchar(50) NOT NULL COMMENT '마스터 내 식별 코드',
  `category_name` varchar(100) NOT NULL COMMENT '카테고리 명칭 (예: 성적 이의, 행정 처분, 기타)',
  `description` varchar(300) DEFAULT NULL COMMENT '카테고리 안내 문구',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서 (오름차순)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_complaint_cat_code` (`complaint_master_id`,`category_code`),
  KEY `idx_complaint_cat_list` (`complaint_master_id`,`sort_order`,`use_yn`,`delete_yn`),
  CONSTRAINT `fk_complaint_cat_master` FOREIGN KEY (`complaint_master_id`) REFERENCES `tb_complaint_master` (`complaint_master_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='민원 카테고리';

/*Table structure for table `tb_complaint_master` */

DROP TABLE IF EXISTS `tb_complaint_master`;

CREATE TABLE `tb_complaint_master` (
  `complaint_master_id` varchar(40) NOT NULL COMMENT '민원 마스터 ID (UUID v7)',
  `site_id` varchar(40) NOT NULL COMMENT '소속 사이트',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `master_code` varchar(50) NOT NULL COMMENT '사이트 내 식별 코드 (영문/숫자/언더스코어)',
  `master_name` varchar(100) NOT NULL COMMENT '게시판 명칭 (예: 이의제기 민원)',
  `DESCRIPTION` varchar(500) DEFAULT NULL COMMENT '게시판 안내 문구 / 관리자 메모',
  `menu_id` varchar(40) DEFAULT NULL COMMENT '메뉴 ID',
  `allow_member_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '일반 회원 로그인 허용' CHECK (`allow_member_yn` in ('Y','N')),
  `allow_oauth2_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT 'OAuth2 소셜 로그인 허용' CHECK (`allow_oauth2_yn` in ('Y','N')),
  `allow_nice_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT 'NICE 실명인증 단독 허용' CHECK (`allow_nice_yn` in ('Y','N')),
  `notice_html` text DEFAULT NULL COMMENT '주의사항 안내문',
  `file_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '첨부파일 허용' CHECK (`file_yn` in ('Y','N')),
  `file_count_max` int(11) NOT NULL DEFAULT 5 COMMENT '최대 첨부 파일 수',
  `file_size_max` bigint(20) NOT NULL DEFAULT 20485760 COMMENT '파일 1개 최대 크기(byte, 기본 20MB)',
  `show_in_list` char(1) DEFAULT 'N' COMMENT '목록 출력 여부 — Y 면 사용자 화면에 순번·제목·등록일만 노출' CHECK (`show_in_list` in ('Y','N')),
  `captcha_yn` char(1) NOT NULL DEFAULT 'N' COMMENT 'captcha 사용' CHECK (`captcha_yn` in ('Y','N')),
  `answer_required_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '답변 필수 여부 (SLA 관리 시 Y)' CHECK (`answer_required_yn` in ('Y','N')),
  `answer_deadline_days` int(11) DEFAULT NULL COMMENT '답변 기한(일). NULL=무제한',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`complaint_master_id`),
  UNIQUE KEY `uk_complaint_master_code` (`site_id`,`master_code`),
  KEY `idx_complaint_master_site` (`site_id`,`use_yn`,`delete_yn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='민원(이의제기) 게시판 마스터 — 사이트별 설정';

/*Table structure for table `tb_content` */

DROP TABLE IF EXISTS `tb_content`;

CREATE TABLE `tb_content` (
  `content_id` varchar(40) NOT NULL COMMENT '콘텐츠 ID (UUID v7)',
  `site_id` varchar(40) NOT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `menu_id` varchar(40) DEFAULT NULL COMMENT '메뉴 ID',
  `title` varchar(300) NOT NULL COMMENT '제목',
  `slug` varchar(200) DEFAULT NULL COMMENT 'URL slug',
  `BODY` mediumtext DEFAULT NULL COMMENT '본문 (WYSIWYG, sanitized)',
  `original_content` text DEFAULT NULL COMMENT '원본 콘텐츠 MD (Markdown 원본 — body 는 렌더된 HTML)',
  `summary` varchar(1000) DEFAULT NULL COMMENT '요약',
  `body_hash` char(64) DEFAULT NULL COMMENT '디스크 HTML 원문 SHA-256(hex). 동기화 변경 감지 키. NULL 이면 강제 sync 대상',
  `meta_keywords` varchar(500) DEFAULT NULL COMMENT 'SEO keywords',
  `meta_description` varchar(500) DEFAULT NULL COMMENT 'SEO description',
  `STATUS` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '상태' CHECK (`STATUS` in ('DRAFT','REVIEW','APPROVED','PUBLISHED','UNPUBLISHED')),
  `published_at` datetime DEFAULT NULL COMMENT '게시 일시',
  `publish_scheduled_at` datetime DEFAULT NULL COMMENT '예약 발행 일시',
  `unpublish_at` datetime DEFAULT NULL COMMENT '게시 만료 일시',
  `view_count` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '조회수',
  `version_no` int(11) NOT NULL DEFAULT 1 COMMENT '현재 버전 번호',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`content_id`),
  UNIQUE KEY `uk_content_slug` (`site_id`,`slug`),
  KEY `idx_content_menu` (`menu_id`,`STATUS`),
  KEY `idx_content_status_pub` (`STATUS`,`published_at`),
  FULLTEXT KEY `ft_content` (`title`,`BODY`,`summary`),
  CONSTRAINT `fk_content_menu` FOREIGN KEY (`menu_id`) REFERENCES `tb_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='콘텐츠 페이지';

/*Table structure for table `tb_content_history` */

DROP TABLE IF EXISTS `tb_content_history`;

CREATE TABLE `tb_content_history` (
  `content_history_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `content_id` varchar(40) NOT NULL COMMENT '콘텐츠 ID',
  `version_no` int(11) NOT NULL COMMENT '버전 번호',
  `title` varchar(300) NOT NULL COMMENT '제목 스냅샷',
  `BODY` mediumtext DEFAULT NULL COMMENT '본문 스냅샷',
  `summary` varchar(1000) DEFAULT NULL COMMENT '요약 스냅샷',
  `changed_by` varchar(40) DEFAULT NULL,
  `change_note` varchar(500) DEFAULT NULL COMMENT '변경 사유 메모',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`content_history_id`),
  UNIQUE KEY `uk_content_hst` (`content_id`,`version_no`),
  CONSTRAINT `fk_content_hst` FOREIGN KEY (`content_id`) REFERENCES `tb_content` (`content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci ROW_FORMAT=DYNAMIC COMMENT='콘텐츠 버전 이력 (압축)' `PAGE_COMPRESSED`=1;

/*Table structure for table `tb_department` */

DROP TABLE IF EXISTS `tb_department`;

CREATE TABLE `tb_department` (
  `department_id` varchar(40) NOT NULL COMMENT '부서 ID (UUID v7)',
  `parent_department_id` varchar(40) DEFAULT NULL COMMENT '상위 부서 ID (NULL=root)',
  `department_code` varchar(30) NOT NULL COMMENT '부서 코드',
  `department_name` varchar(100) NOT NULL COMMENT '부서명',
  `depth` int(11) NOT NULL DEFAULT 1 COMMENT '트리 깊이',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `manager_employee_id` varchar(40) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL COMMENT '설명',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`department_id`),
  UNIQUE KEY `uk_department_code` (`department_code`),
  KEY `idx_department_parent` (`parent_department_id`,`sort_order`),
  CONSTRAINT `fk_department_parent` FOREIGN KEY (`parent_department_id`) REFERENCES `tb_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='부서';

/*Table structure for table `tb_election_voter` */

DROP TABLE IF EXISTS `tb_election_voter`;

CREATE TABLE `tb_election_voter` (
  `voter_id` varchar(40) NOT NULL COMMENT '선거인 ID (UUID v7)',
  `voter_seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '선거인 일련번호 (화면 순번)',
  `election_id` varchar(40) DEFAULT NULL COMMENT '선거 ID — tb_election 도입 시 FK',
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID (멀티사이트 분리)',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `voter_name` varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 성명',
  `birth_date` varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 생년월일 (YYYYMMDD)',
  `resident_no_suffix` varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 주민번호 뒤3자리',
  `voter_name_hash` char(64) NOT NULL COMMENT 'HMAC-SHA256(voter_name) 검색용',
  `birth_date_hash` char(64) NOT NULL COMMENT 'HMAC-SHA256(birth_date) 검색용',
  `resident_no_suffix_hash` char(64) NOT NULL COMMENT 'HMAC-SHA256(resident_no_suffix) 검색용',
  `gender` char(1) NOT NULL COMMENT '성별 (M=남, F=여)' CHECK (`gender` in ('M','F')),
  `voting_district` varchar(50) NOT NULL COMMENT '투표구 (예: 제1투표구)',
  `polling_place` varchar(200) NOT NULL COMMENT '투표장소 (예: 지오넷제1투표소)',
  `polling_location` varchar(255) DEFAULT NULL COMMENT '투표장소 위치 (예: 인앤인1동 102호)',
  `legal_dong_code` varchar(10) DEFAULT NULL COMMENT '법정동 코드 (행정안전부)',
  `legal_dong_name` varchar(100) DEFAULT NULL COMMENT '법정동 명',
  `register_no` varchar(20) DEFAULT NULL COMMENT '등재번호 (선관위 명부)',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`voter_id`),
  UNIQUE KEY `uk_voter_seq` (`voter_seq`),
  KEY `idx_voter_name_hash` (`voter_name_hash`),
  KEY `idx_voter_birth_hash` (`birth_date_hash`),
  KEY `idx_voter_rrn_hash` (`resident_no_suffix_hash`),
  KEY `idx_voter_election` (`election_id`,`delete_yn`),
  KEY `idx_voter_district` (`election_id`,`voting_district`,`delete_yn`),
  KEY `idx_voter_polling` (`election_id`,`polling_place`,`delete_yn`),
  KEY `idx_voter_register` (`register_no`),
  KEY `idx_voter_dong_code` (`legal_dong_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='선거인명부';

/*Table structure for table `tb_election_voter_import_job` */

DROP TABLE IF EXISTS `tb_election_voter_import_job`;

CREATE TABLE `tb_election_voter_import_job` (
  `job_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `election_id` varchar(40) DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_size_bytes` bigint(20) DEFAULT NULL,
  `total_rows` int(11) DEFAULT 0,
  `processed_rows` int(11) DEFAULT 0,
  `success_rows` int(11) DEFAULT 0,
  `fail_rows` int(11) DEFAULT 0,
  `STATUS` varchar(20) NOT NULL DEFAULT 'PENDING',
  `error_log_path` varchar(500) DEFAULT NULL,
  `started_at` timestamp NULL DEFAULT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

/*Table structure for table `tb_employee` */

DROP TABLE IF EXISTS `tb_employee`;

CREATE TABLE `tb_employee` (
  `employee_id` varchar(40) NOT NULL COMMENT '직원 ID (UUID v7)',
  `employee_seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '직원 일련번호 (VIEW uniq_id)',
  `login_id` varchar(50) NOT NULL COMMENT '로그인 ID',
  `PASSWORD` varchar(100) NOT NULL COMMENT '비밀번호 (BCrypt)',
  `password_changed_at` datetime NOT NULL COMMENT '비밀번호 변경 일시',
  `password_expire_at` datetime DEFAULT NULL COMMENT '비밀번호 만료 일시',
  `role_ids` text DEFAULT NULL COMMENT '역할 ID CSV (계층 확장)',
  `group_ids` text DEFAULT NULL COMMENT '부서/그룹 ID CSV',
  `employee_no` varchar(50) DEFAULT NULL COMMENT '사번',
  `employee_name` varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 이름',
  `email` varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 이메일',
  `email_hash` char(64) NOT NULL COMMENT 'HMAC-SHA256(email)',
  `phone` varchar(512) DEFAULT NULL COMMENT '{AG} AES-256-GCM 전화번호',
  `department_id` varchar(40) DEFAULT NULL,
  `department_name` varchar(100) DEFAULT NULL COMMENT '부서 명',
  `POSITION` varchar(100) DEFAULT NULL COMMENT '직위',
  `hire_date` date DEFAULT NULL COMMENT '입사일',
  `resign_date` date DEFAULT NULL COMMENT '퇴사일',
  `two_factor_enabled_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '2FA 활성화 여부' CHECK (`two_factor_enabled_yn` in ('Y','N')),
  `two_factor_secret` varchar(512) DEFAULT NULL COMMENT '{AG} TOTP Secret',
  `ip_whitelist` varchar(2000) DEFAULT NULL COMMENT '허용 IP CIDR CSV',
  `allowed_time_from` time DEFAULT NULL COMMENT '허용 시작 시각',
  `allowed_time_to` time DEFAULT NULL COMMENT '허용 종료 시각',
  `STATUS` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태' CHECK (`STATUS` in ('ACTIVE','LOCKED','INACTIVE','SUSPENDED','RESIGNED')),
  `login_fail_count` int(11) NOT NULL DEFAULT 0 COMMENT '로그인 실패 횟수' CHECK (`login_fail_count` >= 0),
  `locked_until` datetime DEFAULT NULL COMMENT '잠금 해제 예정 일시',
  `captcha_required_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '5회 실패 잠금 해제 후 CAPTCHA 강제 여부 — 다음 성공 시 자동 N',
  `last_login_at` datetime DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '최종 로그인 IP',
  `last_access_at` datetime DEFAULT NULL COMMENT '최종 접속 일시',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`employee_id`),
  UNIQUE KEY `uk_employee_seq` (`employee_seq`),
  UNIQUE KEY `uk_employee_login` (`login_id`),
  UNIQUE KEY `uk_employee_no` (`employee_no`),
  KEY `idx_employee_email_hash` (`email_hash`),
  KEY `idx_employee_dept` (`department_id`),
  KEY `idx_employee_status` (`STATUS`,`delete_yn`),
  CONSTRAINT `chk_employee_captcha_required_yn` CHECK (`captcha_required_yn` in ('Y','N'))
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='직원 (내부 로그인)';

/*Table structure for table `tb_employee_password_history` */

DROP TABLE IF EXISTS `tb_employee_password_history`;

CREATE TABLE `tb_employee_password_history` (
  `pwd_history_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `employee_id` varchar(40) NOT NULL COMMENT '직원 ID (FK)',
  `password_hash` varchar(100) NOT NULL COMMENT 'BCrypt 해시 스냅샷',
  `changed_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '변경 일시',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`pwd_history_id`),
  KEY `idx_emp_pwd_hist_emp` (`employee_id`,`changed_at` DESC),
  CONSTRAINT `fk_emp_pwd_hist_emp` FOREIGN KEY (`employee_id`) REFERENCES `tb_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='직원 비밀번호 이력 (재사용 금지 검증용)';

/*Table structure for table `tb_employee_role` */

DROP TABLE IF EXISTS `tb_employee_role`;

CREATE TABLE `tb_employee_role` (
  `employee_role_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `employee_id` varchar(40) NOT NULL COMMENT '직원 ID',
  `role_id` varchar(40) NOT NULL COMMENT '역할 ID',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`employee_role_id`),
  UNIQUE KEY `uk_employee_role` (`employee_id`,`role_id`),
  KEY `idx_emp_role_role` (`role_id`),
  CONSTRAINT `fk_emp_role_emp` FOREIGN KEY (`employee_id`) REFERENCES `tb_employee` (`employee_id`),
  CONSTRAINT `fk_emp_role_role` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='직원-역할 매핑';

/*Table structure for table `tb_employee_withdraw` */

DROP TABLE IF EXISTS `tb_employee_withdraw`;

CREATE TABLE `tb_employee_withdraw` (
  `employee_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `employee_seq` bigint(20) NOT NULL COMMENT '원 직원 일련번호',
  `login_id` varchar(50) NOT NULL COMMENT '로그인 ID',
  `employee_no` varchar(50) DEFAULT NULL COMMENT '사번',
  `email_hash` char(64) NOT NULL COMMENT '이메일 해시',
  `department_id` varchar(40) DEFAULT NULL,
  `POSITION` varchar(100) DEFAULT NULL COMMENT '직위',
  `hire_date` date DEFAULT NULL COMMENT '입사일',
  `resign_date` date DEFAULT NULL COMMENT '퇴사일',
  `withdraw_at` datetime NOT NULL COMMENT '탈퇴 처리 일시',
  `withdraw_reason` varchar(50) NOT NULL COMMENT '탈퇴 사유',
  `retention_expire_at` datetime NOT NULL COMMENT '보관 만료 일시',
  `withdrawn_by` varchar(40) DEFAULT NULL,
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`employee_id`),
  KEY `idx_emp_withdraw_seq` (`employee_seq`),
  KEY `idx_emp_withdraw_login` (`login_id`),
  KEY `idx_emp_withdraw_expire` (`retention_expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='탈퇴 직원';

/*Table structure for table `tb_file` */

DROP TABLE IF EXISTS `tb_file`;

CREATE TABLE `tb_file` (
  `file_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `file_group_id` varchar(40) NOT NULL COMMENT '파일그룹 ID',
  `original_name` varchar(512) NOT NULL COMMENT '원본 파일명',
  `stored_name` varchar(512) NOT NULL COMMENT '저장 파일명 (UUID v7.ext)',
  `stored_path` varchar(1024) NOT NULL COMMENT '저장 경로 (웹루트 외부)',
  `thumbnail_path` varchar(1024) DEFAULT NULL COMMENT '썸네일 상대경로 (thumbnail root 기준). NULL=없음 (비-이미지 또는 생성 skip)',
  `extension` varchar(20) NOT NULL COMMENT '확장자 (소문자)',
  `mime_detected` varchar(100) NOT NULL COMMENT 'Tika 매직바이트 감지 MIME',
  `mime_client` varchar(100) DEFAULT NULL COMMENT '클라이언트 제시 Content-Type',
  `size_bytes` bigint(20) NOT NULL COMMENT '파일 크기 (바이트)',
  `file_hash` char(64) NOT NULL COMMENT 'SHA-256 해시 (FIM)',
  `original_content` text DEFAULT NULL COMMENT '원본 콘텐츠 MD (Markdown 원본 — 문서 파서 결과)',
  `is_image_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '이미지 여부' CHECK (`is_image_yn` in ('Y','N')),
  `reencoded_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '재인코딩 완료 여부' CHECK (`reencoded_yn` in ('Y','N')),
  `virus_scan_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '바이러스 스캔 상태 (CLEAN / PENDING → 허용)' CHECK (`virus_scan_status` in ('PENDING','CLEAN','INFECTED','ERROR','QUARANTINED','RESCANNING')),
  `download_count` int(10) unsigned NOT NULL DEFAULT 0 COMMENT '다운로드 횟수',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`file_id`),
  KEY `idx_file_group` (`file_group_id`),
  KEY `idx_file_hash` (`file_hash`),
  CONSTRAINT `fk_file_group` FOREIGN KEY (`file_group_id`) REFERENCES `tb_file_group` (`file_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='파일';

/*Table structure for table `tb_file_group` */

DROP TABLE IF EXISTS `tb_file_group`;

CREATE TABLE `tb_file_group` (
  `file_group_id` varchar(40) NOT NULL COMMENT '파일그룹 ID (UUID v7)',
  `entity_type` varchar(50) NOT NULL COMMENT '엔티티 타입 (BBS/CNT/MBR/GEMINI_SEARCH/...)',
  `entity_id` varchar(40) DEFAULT NULL,
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `download_auth` varchar(20) NOT NULL DEFAULT 'ROLE_MEMBER' COMMENT '다운로드 권한 — ANONYMOUS|ROLE_MEMBER|ROLE_EMPLOYEE|ROLE_STAFF|OWNER_PRIVACY|ROLE_MANAGER|ROLE_ADMIN',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`file_group_id`),
  KEY `idx_filegroup_entity` (`entity_type`,`entity_id`),
  CONSTRAINT `chk_file_group_download_auth` CHECK (`download_auth` in ('ANONYMOUS','ROLE_MEMBER','ROLE_EMPLOYEE','ROLE_STAFF','OWNER_PRIVACY','ROLE_MANAGER','ROLE_ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='파일 그룹';

/*Table structure for table `tb_holiday` */

DROP TABLE IF EXISTS `tb_holiday`;

CREATE TABLE `tb_holiday` (
  `holiday_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `holiday_date` date NOT NULL COMMENT '공휴일 날짜',
  `holiday_name` varchar(100) NOT NULL COMMENT '공휴일 명칭',
  `holiday_type` varchar(20) NOT NULL DEFAULT 'PUBLIC' COMMENT '공휴일 유형' CHECK (`holiday_type` in ('PUBLIC','COMPANY','MEMORIAL','OTHER')),
  `description` varchar(500) DEFAULT NULL COMMENT '설명',
  `year` smallint(6) DEFAULT NULL COMMENT '년도',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`holiday_id`),
  UNIQUE KEY `uk_holiday_year_date_name` (`year`,`holiday_date`,`holiday_name`),
  KEY `idx_holiday_year_date` (`year`,`holiday_date`,`use_yn`,`delete_yn`),
  KEY `idx_holiday_date` (`holiday_date`,`use_yn`,`delete_yn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='공휴일';

/*Table structure for table `tb_mail_template` */

DROP TABLE IF EXISTS `tb_mail_template`;

CREATE TABLE `tb_mail_template` (
  `mail_template_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `template_code` varchar(50) NOT NULL COMMENT '템플릿 코드 (예: MEMBER_WELCOME)',
  `template_name` varchar(100) NOT NULL COMMENT '템플릿 명 (한글)',
  `subject` varchar(500) NOT NULL COMMENT '메일 제목 (Thymeleaf 변수 치환 가능)',
  `body_html` mediumtext DEFAULT NULL COMMENT '메일 본문 HTML (Thymeleaf)',
  `sender_email` varchar(255) DEFAULT NULL COMMENT '발신자 이메일 (NULL=기본)',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '발신자 표시명 (NULL=기본)',
  `description` varchar(1000) DEFAULT NULL COMMENT '용도/이벤트 설명',
  `variables_hint` varchar(2000) DEFAULT NULL COMMENT '사용 가능한 변수 목록 힌트 (JSON or CSV)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정일시',
  PRIMARY KEY (`mail_template_id`),
  UNIQUE KEY `uk_mail_template_code` (`template_code`),
  KEY `idx_mail_template_use` (`use_yn`,`delete_yn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='메일 템플릿 (Thymeleaf HTML)';

/*Table structure for table `tb_member` */

DROP TABLE IF EXISTS `tb_member`;

CREATE TABLE `tb_member` (
  `member_id` varchar(40) NOT NULL COMMENT '회원 ID (UUID v7)',
  `member_seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '회원 일련번호 (VIEW uniq_id)',
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `login_id` varchar(50) NOT NULL COMMENT '로그인 ID',
  `PASSWORD` varchar(100) NOT NULL COMMENT '비밀번호 (BCrypt)',
  `password_changed_at` datetime NOT NULL COMMENT '비밀번호 변경 일시',
  `password_expire_at` datetime DEFAULT NULL COMMENT '비밀번호 만료 일시',
  `role_ids` text DEFAULT NULL COMMENT '역할 ID CSV (계층 확장)',
  `group_ids` text DEFAULT NULL COMMENT '그룹 ID CSV (등급/혜택)',
  `member_name` varchar(150) DEFAULT NULL COMMENT '회원 이름 (평문)',
  `nickname` varchar(100) DEFAULT NULL COMMENT '닉네임',
  `email` varchar(512) DEFAULT NULL COMMENT '{AG} AES-256-GCM 이메일',
  `email_hash` char(64) DEFAULT NULL COMMENT 'HMAC-SHA256(email)',
  `email_verified_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '이메일 인증 여부' CHECK (`email_verified_yn` in ('Y','N')),
  `phone` varchar(512) DEFAULT NULL COMMENT '{AG} AES-256-GCM 전화번호',
  `phone_hash` char(64) DEFAULT NULL COMMENT 'HMAC-SHA256(phone)',
  `birth_date` varchar(512) DEFAULT NULL COMMENT '{AG} AES-256-GCM 생년월일',
  `birth_year` char(4) DEFAULT NULL COMMENT 'YYYY 4자리 평문 — 연령대 통계용 (PIPA 일반 개인정보, 암호화 의무 X)',
  `gender` char(1) DEFAULT NULL COMMENT '성별 (M/F/N)' CHECK (`gender` in ('M','F','N')),
  `di` varchar(512) DEFAULT NULL COMMENT '{AG} 본인인증 CI',
  `di_hash` varchar(64) DEFAULT NULL COMMENT 'HMAC-SHA256(ci) 중복가입 방지',
  `parent_name` varchar(150) DEFAULT NULL COMMENT '14세 미만 회원의 법정대리인(부모) 이름 (평문)',
  `parent_di` varchar(512) DEFAULT NULL COMMENT '{AG} 14세 미만 회원의 법정대리인(부모) 본인확인 CI',
  `parent_di_hash` char(64) DEFAULT NULL COMMENT 'HMAC-SHA256(parent_ci) — 부모 CI 중복확인·UNIQUE 키용',
  `address_zipcode` varchar(10) DEFAULT NULL COMMENT '우편번호',
  `address` varchar(1024) DEFAULT NULL COMMENT '{AG} 기본주소',
  `address_detail` varchar(1024) DEFAULT NULL COMMENT '{AG} 상세주소',
  `join_type` varchar(20) NOT NULL DEFAULT 'HOMEPAGE' COMMENT '가입 유형',
  `STATUS` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태' CHECK (`STATUS` in ('ACTIVE','LOCKED','EMAIL_PENDING','SUSPENDED')),
  `login_fail_count` int(11) NOT NULL DEFAULT 0 COMMENT '로그인 실패 횟수' CHECK (`login_fail_count` >= 0),
  `locked_until` datetime DEFAULT NULL COMMENT '잠금 해제 예정 일시',
  `captcha_required_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '5회 실패 잠금 해제 후 CAPTCHA 강제 여부 — 다음 성공 시 자동 N',
  `last_login_at` datetime DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '최종 로그인 IP',
  `last_access_at` datetime DEFAULT NULL COMMENT '최종 접속 일시 (휴면 판정용)',
  `dormant_scheduled_at` datetime DEFAULT NULL COMMENT '휴면 전환 예정 통지 일시',
  `privacy_agree_yn` char(1) NOT NULL COMMENT '개인정보 수집 동의' CHECK (`privacy_agree_yn` in ('Y','N')),
  `terms_agree_yn` char(1) NOT NULL COMMENT '이용약관 동의' CHECK (`terms_agree_yn` in ('Y','N')),
  `marketing_agree_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '마케팅 수신 동의' CHECK (`marketing_agree_yn` in ('Y','N')),
  `sms_agree_yn` char(1) NOT NULL DEFAULT 'N' COMMENT 'SMS 수신 동의' CHECK (`sms_agree_yn` in ('Y','N')),
  `email_agree_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '이메일 수신 동의' CHECK (`email_agree_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_member_seq` (`member_seq`),
  UNIQUE KEY `uk_member_login` (`site_id`,`login_id`),
  UNIQUE KEY `uk_member_identity` (`site_id`,`member_name`,`di_hash`,`parent_di_hash`),
  KEY `idx_member_email_hash` (`email_hash`),
  KEY `idx_member_phone_hash` (`phone_hash`),
  KEY `idx_member_last_access` (`last_access_at`),
  KEY `idx_member_status` (`STATUS`,`delete_yn`),
  KEY `idx_member_ci_hash` (`di_hash`),
  KEY `idx_member_parent_ci_hash` (`parent_di_hash`),
  KEY `idx_member_birth_year` (`birth_year`),
  CONSTRAINT `chk_member_join_type` CHECK (`join_type` in ('EMAIL','KAKAO','NAVER','GOOGLE','APPLE','HOMEPAGE','Mobile','Android','iPhone','iPad','iPod','WindowsPhone','BlackBerry','OperaMini','IEMobile')),
  CONSTRAINT `chk_member_captcha_required_yn` CHECK (`captcha_required_yn` in ('Y','N'))
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='활성 회원';

/*Table structure for table `tb_member_consent` */

DROP TABLE IF EXISTS `tb_member_consent`;

CREATE TABLE `tb_member_consent` (
  `member_consent_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `member_id` varchar(40) DEFAULT NULL,
  `consent_type` varchar(30) NOT NULL COMMENT '동의 유형' CHECK (`consent_type` in ('TERMS','PRIVACY','MARKETING','SMS','EMAIL','THIRD_PARTY')),
  `consent_version` varchar(20) NOT NULL COMMENT '약관 버전',
  `agree_yn` char(1) NOT NULL COMMENT '동의 여부' CHECK (`agree_yn` in ('Y','N')),
  `agreed_at` datetime NOT NULL COMMENT '동의 일시',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '동의 클라이언트 IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`member_consent_id`),
  KEY `idx_mbr_consent` (`member_id`,`consent_type`,`agreed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 동의 이력';

/*Table structure for table `tb_member_dormant` */

DROP TABLE IF EXISTS `tb_member_dormant`;

CREATE TABLE `tb_member_dormant` (
  `member_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `member_seq` bigint(20) NOT NULL COMMENT '회원 일련번호',
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `login_id` varchar(50) NOT NULL COMMENT '로그인 ID',
  `PASSWORD` varchar(100) NOT NULL COMMENT '비밀번호 (BCrypt)',
  `password_changed_at` datetime NOT NULL COMMENT '비밀번호 변경 일시',
  `password_expire_at` datetime DEFAULT NULL COMMENT '비밀번호 만료 일시',
  `role_ids` text DEFAULT NULL COMMENT '역할 ID CSV',
  `group_ids` text DEFAULT NULL COMMENT '그룹 ID CSV',
  `member_name` varchar(150) DEFAULT NULL COMMENT '회원 이름 (평문, 휴면 스냅샷)',
  `nickname` varchar(100) DEFAULT NULL COMMENT '닉네임',
  `email` varchar(512) DEFAULT NULL COMMENT '{AG} 이메일',
  `email_hash` char(64) DEFAULT NULL COMMENT '이메일 해시',
  `email_verified_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '이메일 인증 여부',
  `phone` varchar(512) DEFAULT NULL COMMENT '{AG} 전화번호',
  `phone_hash` char(64) DEFAULT NULL COMMENT '전화번호 해시',
  `birth_date` varchar(512) DEFAULT NULL COMMENT '{AG} 생년월일',
  `birth_year` char(4) DEFAULT NULL COMMENT 'YYYY 4자리 평문 — 연령대 통계용 (PIPA 일반 개인정보, 암호화 의무 X)',
  `gender` char(1) DEFAULT NULL COMMENT '성별',
  `di` varchar(512) DEFAULT NULL COMMENT '{AG} DI',
  `di_hash` varchar(64) DEFAULT NULL COMMENT 'DI 해시',
  `parent_name` varchar(150) DEFAULT NULL COMMENT '부모 이름 스냅샷 (휴면 전환 시점)',
  `parent_di` varchar(512) DEFAULT NULL COMMENT '{AG} 부모 DI 스냅샷',
  `parent_di_hash` char(64) DEFAULT NULL COMMENT 'HMAC-SHA256(parent_di) 스냅샷',
  `address_zipcode` varchar(10) DEFAULT NULL COMMENT '우편번호',
  `address` varchar(1024) DEFAULT NULL COMMENT '{AG} 기본주소',
  `address_detail` varchar(1024) DEFAULT NULL COMMENT '{AG} 상세주소',
  `join_type` varchar(20) NOT NULL DEFAULT 'EMAIL' COMMENT '가입 유형',
  `STATUS` varchar(20) NOT NULL DEFAULT 'DORMANT' COMMENT '상태 (DORMANT)',
  `last_login_at` datetime DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '최종 로그인 IP',
  `last_access_at` datetime DEFAULT NULL COMMENT '최종 접속 일시',
  `privacy_agree_yn` char(1) NOT NULL COMMENT '개인정보 동의',
  `terms_agree_yn` char(1) NOT NULL COMMENT '이용약관 동의',
  `marketing_agree_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '마케팅 동의',
  `sms_agree_yn` char(1) NOT NULL DEFAULT 'N' COMMENT 'SMS 동의',
  `email_agree_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '이메일 동의',
  `dormant_at` datetime NOT NULL COMMENT '휴면 전환 일시',
  `dormant_reason` varchar(50) NOT NULL DEFAULT 'INACTIVE_1Y' COMMENT '휴면 전환 사유',
  `restored_at` datetime DEFAULT NULL COMMENT '복귀 요청 처리 일시',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_mbr_dormant_seq` (`member_seq`),
  UNIQUE KEY `uk_mbr_dormant_login` (`site_id`,`login_id`),
  KEY `idx_mbr_dormant_at` (`dormant_at`),
  KEY `idx_member_dormant_birth_year` (`birth_year`),
  KEY `idx_mbr_dormant_parent_ci_hash` (`parent_di_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='휴면 회원 (분리 보관)';

/*Table structure for table `tb_member_dormant_notice` */

DROP TABLE IF EXISTS `tb_member_dormant_notice`;

CREATE TABLE `tb_member_dormant_notice` (
  `notice_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `member_id` varchar(40) NOT NULL COMMENT '회원 ID (FK, CASCADE)',
  `stage` varchar(10) NOT NULL COMMENT '30D / 7D / 1D' CHECK (`stage` in ('30D','7D','1D')),
  `sent_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '발송 일시',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`notice_id`),
  UNIQUE KEY `uk_mdn_member_stage` (`member_id`,`stage`),
  KEY `idx_mdn_sent_at` (`sent_at`),
  CONSTRAINT `fk_mdn_member` FOREIGN KEY (`member_id`) REFERENCES `tb_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 휴면 전환 안내 메일 발송 이력';

/*Table structure for table `tb_member_oauth` */

DROP TABLE IF EXISTS `tb_member_oauth`;

CREATE TABLE `tb_member_oauth` (
  `member_oauth_id` varchar(40) NOT NULL DEFAULT uuid_v7() COMMENT 'OAuth 매핑 UUID v7 (PK)',
  `member_id` varchar(40) NOT NULL COMMENT 'tb_member.member_id FK',
  `provider` varchar(20) NOT NULL COMMENT 'NAVER / KAKAO / GOOGLE',
  `provider_user_id` varchar(255) NOT NULL COMMENT 'provider 측 사용자 식별자 (sub/id)',
  `email_at_link` varchar(255) DEFAULT NULL COMMENT '연결 당시 provider email (감사용, 평문 보관)',
  `name_at_link` varchar(255) DEFAULT NULL COMMENT '연결 당시 provider name (감사용)',
  `linked_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '연결 일시',
  `last_login_at` datetime DEFAULT NULL COMMENT '직전 OAuth 로그인 일시',
  `use_yn` char(1) NOT NULL DEFAULT 'Y',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부 Y삭제',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(45) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(45) DEFAULT NULL,
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`member_oauth_id`),
  UNIQUE KEY `uk_oauth_provider_user` (`provider`,`provider_user_id`,`delete_yn`),
  KEY `idx_oauth_member` (`member_id`,`delete_yn`),
  CONSTRAINT `fk_oauth_member` FOREIGN KEY (`member_id`) REFERENCES `tb_member` (`member_id`),
  CONSTRAINT `chk_oauth_provider` CHECK (`provider` in ('NAVER','KAKAO','GOOGLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 OAuth2 외부 계정 매핑';

 

CREATE TABLE `tb_member_otp` (
  `otp_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OTP ID (MOT_ + UUIDv7)',
  `member_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '대상 회원 ID (tb_member 또는 tb_member_dormant)',
  `site_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID',
  `purpose` varchar(30) NOT NULL COMMENT '용도 — 교차 사용 방지',
  `code_hash` char(64) NOT NULL COMMENT 'HMAC-SHA256(코드) — 평문 저장 금지',
  `expires_at` datetime NOT NULL COMMENT '만료 일시 (발급 + TTL)',
  `attempt_count` int(11) NOT NULL DEFAULT 0 COMMENT '검증 시도 횟수 (세션이 아니라 행에 둔다)',
  `verified_at` datetime DEFAULT NULL COMMENT '검증 성공 일시 (NULL = 미사용)',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '발급 요청 IP',
  `created_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`otp_id`),
  KEY `idx_otp_member_purpose` (`member_id`,`purpose`,`created_at`),
  KEY `idx_otp_expires` (`expires_at`),
  CONSTRAINT `chk_otp_purpose` CHECK (`purpose` in ('DORMANT_RESTORE','EMAIL_VERIFY','PASSWORD_RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 인증번호(OTP) — 평문 미보관, 시도 횟수는 행에 둔다'




/*Table structure for table `tb_member_password_history` */

DROP TABLE IF EXISTS `tb_member_password_history`;

CREATE TABLE `tb_member_password_history` (
  `pwd_history_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `member_id` varchar(40) DEFAULT NULL,
  `password_hash` varchar(100) NOT NULL COMMENT '비밀번호 해시 (BCrypt)',
  `changed_at` datetime NOT NULL COMMENT '변경 일시',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`pwd_history_id`),
  KEY `idx_mbr_pwd_hst` (`member_id`,`changed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 비밀번호 이력 (재사용 방지)';

/*Table structure for table `tb_member_withdraw` */

DROP TABLE IF EXISTS `tb_member_withdraw`;

CREATE TABLE `tb_member_withdraw` (
  `member_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `member_seq` bigint(20) NOT NULL COMMENT '원 회원 일련번호',
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `login_id_hash` char(64) NOT NULL COMMENT 'HMAC-SHA256(login_id) 부정가입 추적',
  `di_hash` varchar(64) DEFAULT NULL COMMENT '본인 재가입 탐지용 DI 해시',
  `withdraw_at` datetime NOT NULL COMMENT '탈퇴 일시',
  `withdraw_reason` varchar(500) DEFAULT NULL COMMENT '탈퇴 사유',
  `withdraw_status` varchar(50) DEFAULT NULL COMMENT '탈퇴 상태',
  `retention_expire_at` datetime NOT NULL COMMENT '보관 만료 일시 (파기 예정)',
  `legal_basis` varchar(100) DEFAULT NULL COMMENT '보관 근거 법령 조항',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`member_id`),
  KEY `idx_mbr_wd_seq` (`member_seq`),
  KEY `idx_mbr_wd_expire` (`retention_expire_at`),
  KEY `idx_mbr_wd_di_hash` (`di_hash`),
  CONSTRAINT `chk_mbr_wd_status` CHECK (`withdraw_status` is null or `withdraw_status` in ('USER_REQUEST','ADMIN_FORCE','DORMANT_EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='탈퇴 회원 (법정 최소항목만)';

/*Table structure for table `tb_menu` */

DROP TABLE IF EXISTS `tb_menu`;

CREATE TABLE `tb_menu` (
  `menu_id` varchar(40) NOT NULL COMMENT '메뉴 ID (UUID v7)',
  `site_id` varchar(40) NOT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `parent_menu_id` varchar(40) DEFAULT NULL,
  `menu_name` varchar(100) NOT NULL COMMENT '메뉴 명',
  `menu_type` varchar(20) NOT NULL COMMENT '메뉴 타입' CHECK (`menu_type` in ('CONTENT','BOARD','URL','FOLDER')),
  `link_target_id` varchar(40) DEFAULT NULL,
  `link_url` varchar(1000) DEFAULT NULL COMMENT '직접 링크 URL (menu_type=URL)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `depth` int(11) NOT NULL DEFAULT 1 COMMENT '트리 깊이',
  `auth_required_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '인증 필요 여부' CHECK (`auth_required_yn` in ('Y','N')),
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`menu_id`),
  KEY `idx_menu_site_parent` (`site_id`,`parent_menu_id`,`sort_order`),
  KEY `idx_menu_active` (`site_id`,`use_yn`,`delete_yn`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='메뉴 (계층 트리)';

/*Table structure for table `tb_noti_send` */

DROP TABLE IF EXISTS `tb_noti_send`;

CREATE TABLE `tb_noti_send` (
  `noti_send_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `noti_template_id` varchar(40) NOT NULL COMMENT '템플릿 ID',
  `CHANNEL` varchar(20) NOT NULL COMMENT '채널',
  `recipient_user_id` varchar(40) DEFAULT NULL,
  `recipient_address` varchar(512) NOT NULL COMMENT '{AG} 수신 주소 (이메일/휴대폰)',
  `SUBJECT` varchar(500) DEFAULT NULL COMMENT '제목 (최종 렌더링)',
  `BODY` mediumtext NOT NULL COMMENT '본문 (최종 렌더링)',
  `variables_json` longtext DEFAULT NULL COMMENT '치환 변수 (JSON)' CHECK (`variables_json` is null or json_valid(`variables_json`)),
  `scheduled_at` datetime DEFAULT NULL COMMENT '예약 발송 일시',
  `sent_at` datetime DEFAULT NULL COMMENT '실제 발송 일시',
  `STATUS` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태' CHECK (`STATUS` in ('PENDING','QUEUED','SENT','FAIL','CANCELED')),
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '실패 사유',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`noti_send_id`),
  KEY `idx_noti_send_status` (`STATUS`,`scheduled_at`),
  KEY `idx_noti_send_recip` (`recipient_user_id`,`created_at`),
  KEY `fk_noti_send_tpl` (`noti_template_id`),
  CONSTRAINT `fk_noti_send_tpl` FOREIGN KEY (`noti_template_id`) REFERENCES `tb_noti_template` (`noti_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='알림 발송 요청';

/*Table structure for table `tb_noti_template` */

DROP TABLE IF EXISTS `tb_noti_template`;

CREATE TABLE `tb_noti_template` (
  `noti_template_id` varchar(40) NOT NULL COMMENT '알림템플릿 ID (UUID v7)',
  `template_code` varchar(50) NOT NULL COMMENT '템플릿 코드',
  `template_name` varchar(100) NOT NULL COMMENT '템플릿 명',
  `CHANNEL` varchar(20) NOT NULL COMMENT '채널' CHECK (`CHANNEL` in ('EMAIL','SMS','PUSH','ALIMTALK')),
  `subject_template` varchar(500) DEFAULT NULL COMMENT '제목 템플릿 ({{var}} 치환)',
  `body_template` mediumtext NOT NULL COMMENT '본문 템플릿',
  `variable_schema` longtext DEFAULT NULL COMMENT '변수 스키마 (JSON)' CHECK (`variable_schema` is null or json_valid(`variable_schema`)),
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`noti_template_id`),
  UNIQUE KEY `uk_noti_template` (`template_code`,`CHANNEL`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='알림 템플릿';

/*Table structure for table `tb_notification` */

DROP TABLE IF EXISTS `tb_notification`;

CREATE TABLE `tb_notification` (
  `notification_id` varchar(40) NOT NULL COMMENT '알림 PK (UUID v7)',
  `recipient_user_id` varchar(40) NOT NULL COMMENT '수신자 user_seq — 회원/직원/관리자 공통',
  `notification_type` varchar(40) NOT NULL COMMENT 'BOARD_REPORT / BOARD_COMMENT / SURVEY_RESPONSE / SYSTEM / DORMANT_WARN / FILE_INFECTED 등',
  `title` varchar(200) NOT NULL COMMENT '알림 제목 — 인박스 list 노출',
  `BODY` varchar(2000) DEFAULT NULL COMMENT '알림 본문 — 옵션, 인박스 detail 노출',
  `link_url` varchar(500) DEFAULT NULL COMMENT '클릭 시 이동할 상대 경로 — 옵션',
  `related_entity` varchar(40) DEFAULT NULL COMMENT '관련 엔티티 타입 — BBS_ARTICLE / SURVEY 등 옵션',
  `related_id` varchar(40) DEFAULT NULL COMMENT '관련 엔티티 PK — 옵션',
  `read_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '읽음 여부',
  `read_at` datetime DEFAULT NULL COMMENT '읽음 처리 시각',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT 'soft delete',
  `created_by` varchar(40) NOT NULL COMMENT '발신자 user_seq 또는 SYSTEM',
  `created_ip` varchar(45) NOT NULL COMMENT '발신 IP',
  `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '생성 시각',
  `updated_by` varchar(40) NOT NULL COMMENT '최종 수정자',
  `updated_ip` varchar(45) NOT NULL COMMENT '최종 수정 IP',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '최종 수정 시각',
  PRIMARY KEY (`notification_id`),
  KEY `idx_notification_recipient` (`recipient_user_id`,`read_yn`,`delete_yn`,`created_at` DESC),
  KEY `idx_notification_related` (`related_entity`,`related_id`),
  KEY `idx_notification_type` (`notification_type`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='사용자별 알림 인박스 — 다채널의 경우 발송 이력은 tb_noti_send, 본 테이블은 in-app 인박스';

/*Table structure for table `tb_notification_pref` */

DROP TABLE IF EXISTS `tb_notification_pref`;

CREATE TABLE `tb_notification_pref` (
  `pref_id` varchar(40) NOT NULL COMMENT 'PK (UUID v7)',
  `user_id` varchar(40) NOT NULL COMMENT 'v_user_login.user_id (회원/직원/관리자 공통)',
  `notification_type` varchar(40) NOT NULL COMMENT 'NotificationType enum 값 또는 ALL (전체 기본값)',
  `channel_inapp_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT 'in-app 인박스 채널',
  `channel_email_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT 'email 채널',
  `created_by` varchar(40) NOT NULL,
  `created_ip` varchar(45) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_by` varchar(40) NOT NULL,
  `updated_ip` varchar(45) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`pref_id`),
  UNIQUE KEY `uk_pref_user_type` (`user_id`,`notification_type`),
  KEY `idx_pref_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='사용자별 알림 채널 preference — 미등록 시 기본값(in-app=Y/email=Y)';

/*Table structure for table `tb_pii_purge_log` */

DROP TABLE IF EXISTS `tb_pii_purge_log`;

CREATE TABLE `tb_pii_purge_log` (
  `pii_purge_log_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `user_type` varchar(20) NOT NULL COMMENT '사용자 유형' CHECK (`user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF')),
  `user_id_hash` char(64) NOT NULL COMMENT '사용자 ID 해시 (추적 가능하나 역추적 불가)',
  `purged_at` datetime NOT NULL COMMENT '파기 일시',
  `purge_reason` varchar(100) NOT NULL COMMENT '파기 사유',
  `table_list` varchar(500) NOT NULL COMMENT '파기 대상 테이블 목록',
  `legal_basis` varchar(500) DEFAULT NULL COMMENT '파기 근거 법령',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`pii_purge_log_id`),
  KEY `idx_pii_purge_time` (`purged_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='개인정보 파기 이력';

/*Table structure for table `tb_popup` */

DROP TABLE IF EXISTS `tb_popup`;

CREATE TABLE `tb_popup` (
  `popup_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `site_id` varchar(40) NOT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `popup_title` varchar(200) NOT NULL COMMENT '팝업 제목 (관리용)',
  `popup_content` mediumtext DEFAULT NULL COMMENT '팝업 내용 (HTML, sanitized)',
  `popup_type` varchar(20) NOT NULL DEFAULT 'LAYER' COMMENT '팝업 유형' CHECK (`popup_type` in ('LAYER','WINDOW','MODAL','BANNER')),
  `link_url` varchar(1000) DEFAULT NULL COMMENT '링크 URL',
  `file_group_id` varchar(40) DEFAULT NULL COMMENT 'file-picker 의 (entityType=POPUP, entityId=popupId) 그룹 ID. 그룹의 첫 이미지가 노출용',
  `width_px` int(11) DEFAULT NULL COMMENT '너비(px)',
  `height_px` int(11) DEFAULT NULL COMMENT '높이(px)',
  `position_x` int(11) DEFAULT NULL COMMENT 'X 좌표',
  `position_y` int(11) DEFAULT NULL COMMENT 'Y 좌표',
  `show_from` datetime NOT NULL COMMENT '노출 시작 일시',
  `show_to` datetime NOT NULL COMMENT '노출 종료 일시',
  `show_days` varchar(20) DEFAULT NULL COMMENT '노출 요일 (MON,TUE...)',
  `show_time_from` time DEFAULT NULL COMMENT '노출 시작 시각',
  `show_time_to` time DEFAULT NULL COMMENT '노출 종료 시각',
  `cookie_days` int(11) NOT NULL DEFAULT 1 COMMENT '"오늘 그만보기" 쿠키 유효일',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`popup_id`),
  KEY `idx_popup_show` (`site_id`,`use_yn`,`show_from`,`show_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='팝업';

/*Table structure for table `tb_role` */

DROP TABLE IF EXISTS `tb_role`;

CREATE TABLE `tb_role` (
  `role_id` varchar(40) NOT NULL COMMENT '역할 ID (UUID v7)',
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `parent_role_id` varchar(40) DEFAULT NULL COMMENT '직계 상위 역할 ID',
  `role_code` varchar(50) NOT NULL COMMENT '역할 코드 (예: ROLE_ADMIN)',
  `role_name` varchar(100) NOT NULL COMMENT '역할 명',
  `DESCRIPTION` varchar(500) DEFAULT NULL COMMENT '설명',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_code` (`site_id`,`role_code`),
  KEY `idx_role_parent` (`parent_role_id`),
  CONSTRAINT `fk_role_parent` FOREIGN KEY (`parent_role_id`) REFERENCES `tb_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='역할 (계층형)';

/*Table structure for table `tb_role_auth` */

DROP TABLE IF EXISTS `tb_role_auth`;

CREATE TABLE `tb_role_auth` (
  `role_auth_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `role_id` varchar(40) NOT NULL COMMENT '역할 ID',
  `auth_id` varchar(40) NOT NULL COMMENT '권한 ID',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`role_auth_id`),
  UNIQUE KEY `uk_role_auth` (`role_id`,`auth_id`),
  KEY `idx_role_auth_a` (`auth_id`),
  CONSTRAINT `fk_role_auth_auth` FOREIGN KEY (`auth_id`) REFERENCES `tb_auth` (`auth_id`),
  CONSTRAINT `fk_role_auth_role` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='역할-권한 매핑';

/*Table structure for table `tb_role_hierarchy` */

DROP TABLE IF EXISTS `tb_role_hierarchy`;

CREATE TABLE `tb_role_hierarchy` (
  `role_hierarchy_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `ancestor_role_id` varchar(40) NOT NULL COMMENT '조상 역할 ID (상위)',
  `descendant_role_id` varchar(40) NOT NULL COMMENT '후손 역할 ID (하위)',
  `depth` int(11) NOT NULL DEFAULT 1 COMMENT '깊이 (0=self, 1=직계, N=간접)' CHECK (`depth` >= 0),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`role_hierarchy_id`),
  UNIQUE KEY `uk_role_hierarchy` (`ancestor_role_id`,`descendant_role_id`),
  KEY `idx_role_hier_descendant` (`descendant_role_id`,`depth`),
  KEY `idx_role_hier_ancestor` (`ancestor_role_id`,`depth`),
  CONSTRAINT `fk_role_hier_anc` FOREIGN KEY (`ancestor_role_id`) REFERENCES `tb_role` (`role_id`),
  CONSTRAINT `fk_role_hier_dec` FOREIGN KEY (`descendant_role_id`) REFERENCES `tb_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='역할 계층 Closure Table (조상-후손 전수)';

/*Table structure for table `tb_role_url_access` */

DROP TABLE IF EXISTS `tb_role_url_access`;

CREATE TABLE `tb_role_url_access` (
  `url_access_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `url_pattern` varchar(500) NOT NULL COMMENT 'Ant 스타일 URL 패턴 (예: /admin/**)',
  `http_method` varchar(10) NOT NULL DEFAULT 'ALL' COMMENT 'HTTP 메서드' CHECK (`http_method` in ('ALL','GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS')),
  `access_type` varchar(20) NOT NULL COMMENT '접근 유형' CHECK (`access_type` in ('PERMIT_ALL','AUTHENTICATED','ANONYMOUS','ROLE','AUTH','IP_ONLY','DENY')),
  `required_roles` text DEFAULT NULL COMMENT 'ROLE 타입용 role_id CSV (하나라도 매칭)',
  `required_auths` text DEFAULT NULL COMMENT 'AUTH 타입용 auth_id CSV',
  `allowed_user_types` varchar(100) DEFAULT NULL COMMENT '허용 user_type CSV (MEMBER,EMPLOYEE,ADMIN)',
  `allowed_ips` text DEFAULT NULL COMMENT 'IP_ONLY 타입용 CIDR CSV',
  `require_csrf_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT 'CSRF 체크 요구' CHECK (`require_csrf_yn` in ('Y','N')),
  `require_2fa_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '2FA 추가 요구' CHECK (`require_2fa_yn` in ('Y','N')),
  `priority` int(11) NOT NULL DEFAULT 100 COMMENT '매칭 우선순위 (낮을수록 먼저)',
  `DESCRIPTION` varchar(500) DEFAULT NULL COMMENT '설명',
  `remarks` varchar(1000) DEFAULT NULL COMMENT '비고',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`url_access_id`),
  UNIQUE KEY `uk_role_url_access` (`site_id`,`url_pattern`,`http_method`),
  KEY `idx_role_url_access_priority` (`use_yn`,`delete_yn`,`priority`),
  KEY `idx_role_url_access_site` (`site_id`,`use_yn`,`priority`),
  CONSTRAINT `chk_role_url_access_roles` CHECK (`access_type` <> 'ROLE' or `required_roles` is not null),
  CONSTRAINT `chk_role_url_access_auths` CHECK (`access_type` <> 'AUTH' or `required_auths` is not null),
  CONSTRAINT `chk_role_url_access_ips` CHECK (`access_type` <> 'IP_ONLY' or `allowed_ips` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='URL 접근제어 규칙 (권한 중심)';

/*Table structure for table `tb_schedule` */

DROP TABLE IF EXISTS `tb_schedule`;

CREATE TABLE `tb_schedule` (
  `schedule_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `schedule_master_id` varchar(40) NOT NULL COMMENT '일정 마스터 FK',
  `schedule_title` varchar(200) NOT NULL COMMENT '일정 제목',
  `schedule_content` mediumtext DEFAULT NULL COMMENT '일정 내용 (HTML, sanitized)',
  `schedule_category` varchar(30) DEFAULT NULL COMMENT '일정 분류 (자유 문자열, 운영 권장: EVENT/MEETING/HOLIDAY/NOTICE)',
  `start_at` datetime NOT NULL COMMENT '시작 일시',
  `end_at` datetime NOT NULL COMMENT '종료 일시',
  `location` varchar(200) DEFAULT NULL COMMENT '장소',
  `link_url` varchar(1000) DEFAULT NULL COMMENT '링크 URL (선택)',
  `all_day_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '종일 일정 여부' CHECK (`all_day_yn` in ('Y','N')),
  `color_code` varchar(10) DEFAULT NULL COMMENT '캘린더 색상 코드 (#RRGGBB)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`schedule_id`),
  KEY `idx_schedule_master` (`schedule_master_id`,`use_yn`,`delete_yn`),
  KEY `idx_schedule_master_range` (`schedule_master_id`,`start_at`,`end_at`,`use_yn`,`delete_yn`),
  KEY `idx_schedule_range` (`start_at`,`end_at`,`use_yn`,`delete_yn`),
  KEY `idx_schedule_category` (`schedule_category`,`use_yn`,`delete_yn`),
  CONSTRAINT `fk_schedule_master` FOREIGN KEY (`schedule_master_id`) REFERENCES `tb_schedule_master` (`schedule_master_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='일정 — 개별 일정 전체 메타 + 시간. site/menu 는 master 를 통해 추적';

/*Table structure for table `tb_schedule_master` */

DROP TABLE IF EXISTS `tb_schedule_master`;

CREATE TABLE `tb_schedule_master` (
  `schedule_master_id` varchar(40) NOT NULL COMMENT '일정 마스터 ID (UUID v7)',
  `site_id` varchar(40) NOT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `menu_id` varchar(40) DEFAULT NULL,
  `master_title` varchar(200) NOT NULL COMMENT '일정 그룹 제목',
  `master_content` mediumtext DEFAULT NULL COMMENT '일정 그룹 헤더/안내 (HTML, sanitized)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`schedule_master_id`),
  KEY `idx_schedule_master_site` (`site_id`,`use_yn`,`delete_yn`),
  KEY `idx_schedule_master_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='일정 마스터 — site/menu + 그룹 헤더 (얇은 owner)';

/*Table structure for table `tb_search_forbidden_word` */

DROP TABLE IF EXISTS `tb_search_forbidden_word`;

CREATE TABLE `tb_search_forbidden_word` (
  `forbidden_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `site_id` varchar(40) DEFAULT NULL COMMENT 'NULL = 전역 차단',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `keyword` varchar(100) NOT NULL COMMENT '금지어 (trim)',
  `match_type` varchar(20) NOT NULL DEFAULT 'CONTAINS' COMMENT 'EXACT/CONTAINS/PREFIX',
  `reason` varchar(500) DEFAULT NULL COMMENT '운영자 메모 (차단 사유)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부 Y삭제',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`forbidden_id`),
  UNIQUE KEY `uk_search_forbidden` (`site_id`,`keyword`,`match_type`),
  KEY `idx_search_forbidden_active` (`site_id`,`use_yn`,`delete_yn`),
  CONSTRAINT `chk_forbidden_match_type` CHECK (`match_type` in ('EXACT','CONTAINS','PREFIX')),
  CONSTRAINT `chk_forbidden_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_forbidden_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='검색 금지어 마스터 (사이트별/전역, 운영자 수동 등록)';

/*Table structure for table `tb_search_gemini_file` */

DROP TABLE IF EXISTS `tb_search_gemini_file`;

CREATE TABLE `tb_search_gemini_file` (
  `gemini_file_id` varchar(40) NOT NULL COMMENT 'PK (UUID v7)',
  `file_id` varchar(40) NOT NULL COMMENT 'FK → tb_file.file_id',
  `dept_id` varchar(40) DEFAULT NULL COMMENT 'NULL=ALL(전체공개), 값=tb_department FK (해당부서+하위만 검색)',
  `category_code` varchar(20) DEFAULT NULL COMMENT '문서 카테고리 (DOC_CATEGORY 공통코드)',
  `display_name` varchar(500) DEFAULT NULL COMMENT '표시용 파일명',
  `description` varchar(2000) DEFAULT NULL COMMENT '파일 설명',
  `gemini_name` varchar(200) DEFAULT NULL COMMENT 'Gemini API name (files/abc123)',
  `gemini_uri` varchar(500) DEFAULT NULL COMMENT 'Gemini API URI',
  `gemini_state` varchar(20) DEFAULT NULL COMMENT 'PROCESSING | ACTIVE | FAILED',
  `gemini_mime_type` varchar(100) DEFAULT NULL COMMENT 'Gemini 업로드 mimeType',
  `gemini_expires_at` timestamp NULL DEFAULT NULL COMMENT 'Gemini 파일 만료 시각 (48 h)',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '소프트 삭제',
  PRIMARY KEY (`gemini_file_id`),
  KEY `idx_gemini_file` (`file_id`),
  KEY `idx_gemini_deleted` (`deleted_at`),
  KEY `idx_gemini_dept` (`dept_id`),
  KEY `idx_gemini_category` (`category_code`),
  CONSTRAINT `fk_gemini_dept` FOREIGN KEY (`dept_id`) REFERENCES `tb_department` (`department_id`),
  CONSTRAINT `fk_gemini_file` FOREIGN KEY (`file_id`) REFERENCES `tb_file` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='Gemini Files API 문서검색 RAG 메타 관리';

/*Table structure for table `tb_search_gemini_keyword` */

DROP TABLE IF EXISTS `tb_search_gemini_keyword`;

CREATE TABLE `tb_search_gemini_keyword` (
  `keyword_id` varchar(40) NOT NULL COMMENT 'PK (UUID v7)',
  `question` text NOT NULL COMMENT '검색어 / 사용자 질의 원문',
  `answer` longtext DEFAULT NULL COMMENT '검색결과 / 최종 답변 (마크다운)',
  `api_model` varchar(100) NOT NULL COMMENT 'Gemini 모델 ID (예: gemini-2.0-flash)',
  `input_tokens` int(11) NOT NULL DEFAULT 0 COMMENT '입력 토큰 합계 (prompt)',
  `output_tokens` int(11) NOT NULL DEFAULT 0 COMMENT '출력 토큰 합계 (candidates)',
  `cached_tokens` int(11) NOT NULL DEFAULT 0 COMMENT '캐시된 입력 토큰 (Context Caching 적용분)',
  `total_tokens` int(11) NOT NULL DEFAULT 0 COMMENT '총 토큰 = input + output',
  `price_per_million_usd` decimal(10,6) NOT NULL DEFAULT 0.250000 COMMENT '1백만 토큰당 가격 USD (INSERT 당시 값 보존)',
  `input_cost_usd` decimal(12,8) NOT NULL DEFAULT 0.00000000 COMMENT 'input 토큰 비용 USD',
  `output_cost_usd` decimal(12,8) NOT NULL DEFAULT 0.00000000 COMMENT 'output 토큰 비용 USD',
  `total_cost_usd` decimal(12,8) NOT NULL DEFAULT 0.00000000 COMMENT '총 비용 USD = input + output',
  `selected_file_ids` text DEFAULT NULL COMMENT '선택된 geminiFileId CSV',
  `selected_file_count` int(11) NOT NULL DEFAULT 0 COMMENT '선택된 파일 개수',
  `cache_used` char(1) NOT NULL DEFAULT 'N' COMMENT 'Context Caching 사용 여부 Y/N',
  `api_call_count` int(11) NOT NULL DEFAULT 0 COMMENT 'generateContent 호출 횟수 (RAG 5회 등)',
  `elapsed_ms` int(11) NOT NULL DEFAULT 0 COMMENT 'AI 처리 응답 시간 (ms)',
  `result_status` varchar(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAIL / PARTIAL',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '실패 시 사유',
  `user_id` varchar(40) DEFAULT NULL COMMENT '질의자 (사용자 UUID)',
  `user_type` varchar(20) DEFAULT NULL COMMENT 'STAFF / EMPLOYEE / MEMBER',
  `user_dept_id` varchar(40) DEFAULT NULL COMMENT '질의자 부서 ID',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`keyword_id`),
  KEY `idx_gkeyword_user` (`user_id`,`created_at`),
  KEY `idx_gkeyword_dept` (`user_dept_id`,`created_at`),
  KEY `idx_gkeyword_model` (`api_model`,`created_at`),
  KEY `idx_gkeyword_created` (`created_at`),
  KEY `idx_gkeyword_status` (`result_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='Gemini AI 질의 로그 + 비용 추적';

/*Table structure for table `tb_search_index` */

DROP TABLE IF EXISTS `tb_search_index`;

CREATE TABLE `tb_search_index` (
  `index_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `entity_type` varchar(20) NOT NULL COMMENT 'BBS_ARTICLE/BBS_COMMENT/CONTENT/MENU/FILE/SCHEDULE',
  `entity_id` varchar(40) DEFAULT NULL COMMENT '대상 엔티티 PK',
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `bbs_master_id` varchar(40) DEFAULT NULL COMMENT '게시판마스터 ID',
  `category_id` varchar(40) DEFAULT NULL COMMENT '카테고리 ID',
  `title` varchar(500) NOT NULL COMMENT '제목 (PII 마스킹 적용)',
  `content_text` mediumtext NOT NULL COMMENT 'HTML strip + PII 마스킹 + 불용어 필터 plain text',
  `writer_name` varchar(100) DEFAULT NULL COMMENT '작성자 표시명 (PII 마스킹 적용)',
  `search_tokens` text DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT '원본 status — PUBLISHED 만 검색 노출',
  `url` varchar(500) NOT NULL COMMENT '클릭 시 이동 URL',
  `thumb_file_id` varchar(40) DEFAULT NULL COMMENT '썸네일 file_id',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부 Y삭제',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`index_id`),
  UNIQUE KEY `uk_search_entity` (`entity_type`,`entity_id`),
  KEY `idx_search_site_status` (`site_id`,`status`,`delete_yn`,`updated_at` DESC),
  KEY `idx_search_bbs` (`bbs_master_id`,`status`,`delete_yn`),
  KEY `idx_search_title` (`title`),
  FULLTEXT KEY `ftx_search_tokens` (`search_tokens`),
  CONSTRAINT `chk_search_index_entity_type` CHECK (`entity_type` in ('BBS_ARTICLE','BBS_COMMENT','CONTENT','MENU','FILE','SCHEDULE')),
  CONSTRAINT `chk_search_index_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB AUTO_INCREMENT=763 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='통합 검색 인덱스 — article/content 등 다도메인 (MariaDB LIKE 기반)';

/*Table structure for table `tb_search_keyword` */

DROP TABLE IF EXISTS `tb_search_keyword`;

CREATE TABLE `tb_search_keyword` (
  `keyword_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `site_id` varchar(40) DEFAULT NULL COMMENT 'NULL = 전역 통계',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `keyword` varchar(100) NOT NULL COMMENT '정규화된 키워드 (trim)',
  `hit_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '누적 검색 횟수',
  `last_hit_at` timestamp NULL DEFAULT current_timestamp(),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`keyword_id`),
  UNIQUE KEY `uk_search_keyword` (`site_id`,`keyword`),
  KEY `idx_search_keyword_hit` (`site_id`,`hit_count` DESC,`last_hit_at` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=118 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='인기검색어 누적 (검색 발생 시 UPSERT)';

/*Table structure for table `tb_search_recommend` */

DROP TABLE IF EXISTS `tb_search_recommend`;

CREATE TABLE `tb_search_recommend` (
  `recommend_id` varchar(40) NOT NULL COMMENT 'UUID v7 (REC- prefix)',
  `site_id` varchar(40) DEFAULT NULL COMMENT 'NULL = 전역 추천',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `keyword` varchar(100) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT 'ASC 노출',
  `description` varchar(500) DEFAULT NULL,
  `use_yn` char(1) NOT NULL DEFAULT 'Y',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부 Y삭제',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`recommend_id`),
  KEY `idx_search_recommend_active` (`site_id`,`use_yn`,`delete_yn`,`sort_order`),
  CONSTRAINT `chk_recommend_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_recommend_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='추천검색어 마스터 (운영자 수동 등록, 사이트별/전역)';

/*Table structure for table `tb_search_synonym` */

DROP TABLE IF EXISTS `tb_search_synonym`;

CREATE TABLE `tb_search_synonym` (
  `synonym_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `site_id` varchar(40) DEFAULT NULL COMMENT 'NULL = 전역',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `canonical` varchar(100) NOT NULL COMMENT '대표어',
  `synonyms` text NOT NULL COMMENT '동의어 목록 (줄바꿈 또는 콤마 구분)',
  `bidirectional_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT 'Y=양방향, N=단방향',
  `description` varchar(500) DEFAULT NULL COMMENT '운영자 메모',
  `use_yn` char(1) NOT NULL DEFAULT 'Y',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부 Y삭제',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`synonym_id`),
  UNIQUE KEY `uk_search_synonym` (`site_id`,`canonical`),
  KEY `idx_search_synonym_active` (`site_id`,`use_yn`,`delete_yn`),
  CONSTRAINT `chk_synonym_bidir` CHECK (`bidirectional_yn` in ('Y','N')),
  CONSTRAINT `chk_synonym_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_synonym_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='검색 동의어/유의어 사전 (운영자 수동 등록)';



CREATE TABLE `tb_site` (
  `site_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID (SIT_ + UUIDv7)',
  `site_code` varchar(30) NOT NULL COMMENT '사이트 코드 (URL 경로 식별자 /{siteCode}/… — 소문자·숫자·하이픈)',
  `site_name` varchar(100) NOT NULL COMMENT '사이트 명',
  `domain` varchar(255) DEFAULT NULL COMMENT '커스텀 도메인 (소문자 저장 — siteCode 판별 보조, canonical 은 경로. conventions.md §5)',
  `default_lang` varchar(10) NOT NULL DEFAULT 'ko' COMMENT '기본 언어 (ko/en/ja/zh)',
  `parent_site_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '부모 사이트 (다국어 변형·서브사이트 트리, NULL=대표)',
  `template_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '선택 템플릿 (NULL=미선택 → krds 기본 템플릿 폴백)',
  `theme_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '선택 테마 (NULL=템플릿 기본 브랜드. 소속 검증=fk_site_theme 복합 FK)',
  `layout_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '선택 레이아웃 (NULL=템플릿 기본 레이아웃)',
  `default_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '기본 사이트 — 도메인·경로 미해석 시 폴백 (전체 1개, 앱 검증)' CHECK (`default_yn` in ('Y','N')),
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '관리 목록 정렬',
  `logo_path` varchar(255) DEFAULT NULL COMMENT '로고 이미지 경로 (마스트헤드, NULL=사이트명 텍스트)',
  `favicon_path` varchar(255) DEFAULT NULL COMMENT '파비콘 경로 (NULL=시스템 기본)',
  `description` varchar(500) DEFAULT NULL COMMENT '설명',
  `head_meta` text DEFAULT NULL COMMENT '사이트별 <head> 삽입 HTML 조각 (meta/link/script). 관리자 textarea 편집, th:utext 출력',
  `copyright` text DEFAULT NULL COMMENT '사이트별 footer copyright 문구 (HTML 허용)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (소프트 삭제)' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`site_id`),
  UNIQUE KEY `uk_site_code` (`site_code`),
  UNIQUE KEY `uk_site_domain` (`domain`),
  KEY `idx_site_template` (`template_id`),
  KEY `idx_site_layout` (`layout_id`),
  KEY `idx_site_parent` (`parent_site_id`),
  KEY `idx_site_active` (`use_yn`,`delete_yn`,`sort_order`),
  KEY `fk_site_theme` (`template_id`,`theme_id`),
  CONSTRAINT `fk_site_layout` FOREIGN KEY (`layout_id`) REFERENCES `tb_layout` (`layout_id`),
  CONSTRAINT `fk_site_parent` FOREIGN KEY (`parent_site_id`) REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `fk_site_template` FOREIGN KEY (`template_id`) REFERENCES `tb_template` (`template_id`),
  CONSTRAINT `fk_site_theme` FOREIGN KEY (`template_id`, `theme_id`) REFERENCES `tb_theme` (`template_id`, `theme_id`),
  CONSTRAINT `chk_site_code_pattern` CHECK (`site_code` regexp '^[a-z0-9][a-z0-9-]{1,29}$'),
  CONSTRAINT `chk_site_code_reserved` CHECK (`site_code` not in ('adm','api','bbs','member','prg','search','static','css','js','fonts','tmpl','error','actuator'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='사이트 (멀티사이트 마스터)';
  
  
CREATE TABLE `tb_template` (
  `template_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '템플릿 ID (TPL_ + UUIDv7)',
  `template_code` varchar(50) NOT NULL COMMENT '템플릿 코드 (= CSS 파일명 /tmpl/css/{code}.css)',
  `template_name` varchar(100) NOT NULL COMMENT '템플릿 명',
  `default_layout_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '기본 레이아웃 (사이트 layout_id NULL 시 적용)',
  `design_md` text DEFAULT NULL COMMENT 'Claude Design Md (시각 언어 원전)',
  `description` varchar(500) DEFAULT NULL COMMENT '설명',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_template_code` (`template_code`),
  KEY `idx_template_layout` (`default_layout_id`),
  CONSTRAINT `fk_template_layout` FOREIGN KEY (`default_layout_id`) REFERENCES `tb_layout` (`layout_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='템플릿 (시각 언어)'

CREATE TABLE `tb_theme` (
  `theme_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테마 ID (THM_ + UUIDv7)',
  `template_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '소속 템플릿 ID',
  `theme_code` varchar(30) NOT NULL COMMENT '테마 코드 (blue·teal·indigo·green…)',
  `theme_name` varchar(100) NOT NULL COMMENT '테마 명',
  `css_class` varchar(50) NOT NULL DEFAULT '' COMMENT 'html 클래스 ('' = 템플릿 기본 브랜드)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`theme_id`),
  UNIQUE KEY `uk_theme` (`template_id`,`theme_code`),
  UNIQUE KEY `uk_theme_tpl` (`template_id`,`theme_id`),
  CONSTRAINT `fk_theme_template` FOREIGN KEY (`template_id`) REFERENCES `tb_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='테마 (템플릿별 색 변형)';

 
CREATE TABLE `tb_layout` (
  `layout_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '레이아웃 ID (LAY_ + UUIDv7)',
  `layout_code` varchar(50) NOT NULL COMMENT '레이아웃 코드 (layout-001 … = 뷰 폴더명)',
  `layout_name` varchar(100) NOT NULL COMMENT '레이아웃 명',
  `wireframe_ref` varchar(30) DEFAULT NULL COMMENT '와이어프레임 원전 (frame001…)',
  `description` varchar(500) DEFAULT NULL COMMENT '설명',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`layout_id`),
  UNIQUE KEY `uk_layout_code` (`layout_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='레이아웃 (구조 프레임)';


/*Table structure for table `tb_survey` */

DROP TABLE IF EXISTS `tb_survey`;

CREATE TABLE `tb_survey` (
  `survey_id` varchar(40) NOT NULL COMMENT '설문 ID (UUID v7)',
  `survey_master_id` varchar(40) NOT NULL COMMENT '설문 마스터 FK',
  `survey_title` varchar(200) NOT NULL COMMENT '설문 제목',
  `survey_description` mediumtext DEFAULT NULL COMMENT '설문 설명 (HTML, sanitized)',
  `start_at` datetime NOT NULL COMMENT '시작 일시',
  `end_at` datetime NOT NULL COMMENT '종료 일시',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '설문 상태' CHECK (`status` in ('DRAFT','PUBLISHED','CLOSED')),
  `anonymous_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '익명 응답 여부 (Y면 응답자 ID 무관)' CHECK (`anonymous_yn` in ('Y','N')),
  `one_response_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '1인 1회 응답 제한' CHECK (`one_response_yn` in ('Y','N')),
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`survey_id`),
  KEY `idx_survey_master` (`survey_master_id`,`use_yn`,`delete_yn`),
  KEY `idx_survey_master_status` (`survey_master_id`,`status`,`start_at`,`end_at`),
  CONSTRAINT `fk_survey_master` FOREIGN KEY (`survey_master_id`) REFERENCES `tb_survey_master` (`survey_master_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 조사';

/*Table structure for table `tb_survey_answer` */

DROP TABLE IF EXISTS `tb_survey_answer`;

CREATE TABLE `tb_survey_answer` (
  `answer_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `response_id` varchar(40) NOT NULL COMMENT '응답 헤더 ID',
  `question_id` varchar(40) NOT NULL COMMENT '문항 ID',
  `option_id` varchar(40) DEFAULT NULL,
  `answer_text` text DEFAULT NULL COMMENT '주관식 응답',
  `answer_number` int(11) DEFAULT NULL COMMENT 'SCALE 응답',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`answer_id`),
  KEY `idx_answer_response` (`response_id`),
  KEY `idx_answer_question_option` (`question_id`,`option_id`),
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `tb_survey_question` (`question_id`),
  CONSTRAINT `fk_answer_response` FOREIGN KEY (`response_id`) REFERENCES `tb_survey_response` (`response_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 응답 상세';

/*Table structure for table `tb_survey_master` */

DROP TABLE IF EXISTS `tb_survey_master`;

CREATE TABLE `tb_survey_master` (
  `survey_master_id` varchar(40) NOT NULL COMMENT '설문 마스터 ID (UUID v7)',
  `site_id` varchar(40) NOT NULL COMMENT '사이트 ID',
  `site_code` varchar(30) DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `menu_id` varchar(40) DEFAULT NULL,
  `master_title` varchar(200) NOT NULL COMMENT '설문 그룹 제목',
  `master_content` mediumtext DEFAULT NULL COMMENT '설문 그룹 헤더/안내 (HTML, sanitized)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`survey_master_id`),
  KEY `idx_survey_master_site` (`site_id`,`use_yn`,`delete_yn`),
  KEY `idx_survey_master_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 마스터 — site/menu + 그룹 헤더 (얇은 owner)';

/*Table structure for table `tb_survey_option` */

DROP TABLE IF EXISTS `tb_survey_option`;

CREATE TABLE `tb_survey_option` (
  `option_id` varchar(40) NOT NULL COMMENT 'ID (UUID v7)',
  `question_id` varchar(40) NOT NULL COMMENT '문항 ID',
  `option_text` varchar(500) NOT NULL COMMENT '선택지 내용',
  `option_value` varchar(100) DEFAULT NULL COMMENT '선택지 값 (코드)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`option_id`),
  KEY `idx_option_question` (`question_id`,`sort_order`,`delete_yn`),
  CONSTRAINT `fk_option_question` FOREIGN KEY (`question_id`) REFERENCES `tb_survey_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 선택지';

/*Table structure for table `tb_survey_question` */

DROP TABLE IF EXISTS `tb_survey_question`;

CREATE TABLE `tb_survey_question` (
  `question_id` varchar(40) NOT NULL COMMENT '문항 ID',
  `survey_id` varchar(40) NOT NULL COMMENT '설문 ID',
  `question_text` varchar(1000) NOT NULL COMMENT '문항 내용',
  `question_type` varchar(20) NOT NULL DEFAULT 'TEXT' COMMENT '문항 유형' CHECK (`question_type` in ('TEXT','TEXTAREA','RADIO','CHECKBOX','SELECT','SCALE')),
  `required_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '필수 응답 여부' CHECK (`required_yn` in ('Y','N')),
  `scale_min` int(11) DEFAULT NULL COMMENT 'SCALE 최소값',
  `scale_max` int(11) DEFAULT NULL COMMENT 'SCALE 최대값',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`question_id`),
  KEY `idx_question_survey` (`survey_id`,`sort_order`,`delete_yn`),
  CONSTRAINT `fk_question_survey` FOREIGN KEY (`survey_id`) REFERENCES `tb_survey` (`survey_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 문항';

/*Table structure for table `tb_survey_response` */

DROP TABLE IF EXISTS `tb_survey_response`;

CREATE TABLE `tb_survey_response` (
  `response_id` varchar(40) NOT NULL COMMENT '응답 ID',
  `survey_id` varchar(40) NOT NULL COMMENT '설문 ID',
  `member_id` varchar(40) DEFAULT NULL,
  `client_ip` varchar(50) DEFAULT NULL COMMENT '응답자 IP',
  `submitted_at` timestamp NULL DEFAULT current_timestamp() COMMENT '제출 시각',
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`response_id`),
  UNIQUE KEY `uk_response_survey_member` (`survey_id`,`member_id`),
  KEY `idx_response_survey` (`survey_id`,`submitted_at`),
  CONSTRAINT `fk_response_survey` FOREIGN KEY (`survey_id`) REFERENCES `tb_survey` (`survey_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 응답';
 
 
  
CREATE   VIEW `v_site_menu` AS with recursive menu_tree as (
select `m`.`menu_id` AS `menu_id`,`m`.`parent_menu_id` AS `parent_menu_id`,`m`.`site_id` AS `site_id`,`m`.`menu_name` AS `menu_name`,`m`.`menu_type` AS `menu_type`,`m`.`link_target_id` AS `link_target_id`,`m`.`link_url` AS `link_url`,`m`.`sort_order` AS `sort_order`,`m`.`depth` AS `depth`,`m`.`use_yn` AS `use_yn`,`m`.`auth_required_yn` AS `auth_required_yn`,cast(`m`.`menu_id` as char(2000) charset utf8mb4) AS `root_menu_id`,cast(`m`.`menu_name` as char(2000) charset utf8mb4) AS `breadcrumb_path`,cast(`m`.`menu_id` as char(2000) charset utf8mb4) AS `id_path`,1 AS `path_depth` from `tb_menu` `m` where `m`.`parent_menu_id` is null and `m`.`delete_yn` = 'N' union all select `c`.`menu_id` AS `menu_id`,`c`.`parent_menu_id` AS `parent_menu_id`,`c`.`site_id` AS `site_id`,`c`.`menu_name` AS `menu_name`,`c`.`menu_type` AS `menu_type`,`c`.`link_target_id` AS `link_target_id`,`c`.`link_url` AS `link_url`,`c`.`sort_order` AS `sort_order`,`c`.`depth` AS `depth`,`c`.`use_yn` AS `use_yn`,`c`.`auth_required_yn` AS `auth_required_yn`,`t`.`root_menu_id` AS `root_menu_id`,concat(`t`.`breadcrumb_path`,' > ',`c`.`menu_name`) AS `breadcrumb_path`,concat(`t`.`id_path`,'/',`c`.`menu_id`) AS `id_path`,`t`.`path_depth` + 1 AS `path_depth` from (`tb_menu` `c` join `menu_tree` `t` on(`t`.`menu_id` = `c`.`parent_menu_id`)) where `c`.`delete_yn` = 'N')select `t`.`menu_id` AS `menu_id`,`t`.`parent_menu_id` AS `parent_menu_id`,`t`.`root_menu_id` AS `root_menu_id`,`t`.`site_id` AS `site_id`,`s`.`site_code` AS `site_code`,`s`.`site_name` AS `site_name`,`t`.`menu_name` AS `menu_name`,`t`.`menu_type` AS `menu_type`,`t`.`link_target_id` AS `link_target_id`,`t`.`link_url` AS `link_url`,`t`.`sort_order` AS `sort_order`,`t`.`depth` AS `depth`,`t`.`use_yn` AS `use_yn`,`t`.`auth_required_yn` AS `auth_required_yn`,`ct`.`slug` AS `link_slug`,`ct`.`title` AS `link_content_title`,`bm`.`bbs_code` AS `link_bbs_code`,`bm`.`bbs_name` AS `link_bbs_name`,case ucase(`t`.`menu_type`) when 'CONTENT' then case when `ct`.`slug` is null or `s`.`site_code` is null then NULL else concat('/',`s`.`site_code`,'/',`ct`.`slug`) end when 'BOARD' then case when `bm`.`bbs_code` is null or `s`.`site_code` is null then NULL else concat('/bbs/',`s`.`site_code`,'/',`bm`.`bbs_code`) end when 'URL' then `t`.`link_url` else NULL end AS `canonical_url`,`t`.`breadcrumb_path` AS `breadcrumb_path`,`t`.`id_path` AS `id_path`,`t`.`path_depth` AS `path_depth` from (((`menu_tree` `t` left join `tb_site` `s` on(`s`.`site_id` = `t`.`site_id` and `s`.`delete_yn` = 'N')) left join `tb_content` `ct` on(`ct`.`content_id` = `t`.`link_target_id` and `ct`.`delete_yn` = 'N')) left join `tb_bbs_master` `bm` on(`bm`.`bbs_master_id` = `t`.`link_target_id` and `bm`.`delete_yn` = 'N'));



CREATE   VIEW `v_file` AS 
select  `f`.`file_id` AS `file_id`,  `f`.`file_group_id` AS `file_group_id`,  `g`.`entity_type` AS `entity_type`,  `g`.`entity_id` AS `entity_id`,  `g`.`site_id` AS `site_id`,  `f`.`original_name` AS `original_name`,  `f`.`stored_name` AS `stored_name`,  `f`.`stored_path` AS `stored_path`,  `f`.`thumbnail_path` AS `thumbnail_path`,  `f`.`extension` AS `extension`,  `f`.`mime_detected` AS `mime_detected`,  `f`.`mime_client` AS `mime_client`,  `f`.`size_bytes` AS `size_bytes`,  `f`.`file_hash` AS `file_hash`,  `f`.`is_image_yn` AS `is_image_yn`,  `f`.`reencoded_yn` AS `reencoded_yn`,  `f`.`virus_scan_status` AS `virus_scan_status`,  `f`.`download_count` AS `download_count`,  `f`.`sort_order` AS `sort_order`,  `f`.`created_by` AS `created_by`,  `f`.`created_ip` AS `created_ip`,  `f`.`created_at` AS `created_at`,  `f`.`updated_by` AS `updated_by`,  `f`.`updated_ip` AS `updated_ip`,  `f`.`updated_at` AS `updated_at` from (`tb_file` `f`  join `tb_file_group` `g`  on (`g`.`file_group_id` = `f`.`file_group_id`)) where `f`.`delete_yn` = 'N'  and `g`.`delete_yn` = 'N';



CREATE   VIEW `v_active_admin` AS 
select  `a`.`admin_id` AS `admin_id`,  `a`.`admin_seq` AS `uniq_id`,  `a`.`login_id` AS `login_id`,  `g`.`group_name` AS `group_name`,  `a`.`department_name` AS `department_name`,  `a`.`department_id` AS `department_id`,  `a`.`STATUS` AS `status`,  `a`.`last_login_at` AS `last_login_at`,  `a`.`last_login_ip` AS `last_login_ip`,  `a`.`two_factor_enabled_yn` AS `two_factor_enabled_yn` from (`tb_admin` `a`  join `tb_admin_group` `g`  on (`g`.`admin_group_id` = `a`.`admin_group_id`)) where `a`.`delete_yn` = 'N'  and `a`.`STATUS` = 'ACTIVE';



CREATE   VIEW `v_user_login` AS 
select  'MEMBER' AS `user_type`,  `m`.`member_id` AS `user_id`,  `m`.`member_seq` AS `uniq_id`,  `m`.`site_id` AS `site_id`,  NULL AS `group_id`,  `m`.`login_id` AS `login_id`,  `m`.`PASSWORD` AS `password`,  `m`.`STATUS` AS `status`,  `m`.`login_fail_count` AS `login_fail_count`,  `m`.`locked_until` AS `locked_until`,  `m`.`last_login_at` AS `last_login_at`,  `m`.`password_changed_at` AS `password_changed_at`,  `m`.`password_expire_at` AS `password_expire_at`,  'N' AS `two_factor_enabled_yn`,  NULL AS `two_factor_secret`,  NULL AS `ip_whitelist`,  NULL AS `allowed_time_from`,  NULL AS `allowed_time_to`,  `m`.`role_ids` AS `role_ids`,  'ROLE_MEMBER' AS `role_codes`,  `m`.`group_ids` AS `group_ids`,  '' AS `department_id`,  '' AS `department_name`,  `m`.`delete_yn` AS `delete_yn` from `tb_member` `m` where `m`.`delete_yn` = 'N' union all select  'EMPLOYEE' AS `user_type`,  `e`.`employee_id` AS `user_id`,  `e`.`employee_seq` AS `uniq_id`,  NULL AS `site_id`,  NULL AS `group_id`,  `e`.`login_id` AS `login_id`,  `e`.`PASSWORD` AS `password`,  `e`.`STATUS` AS `status`,  `e`.`login_fail_count` AS `login_fail_count`,  `e`.`locked_until` AS `locked_until`,  `e`.`last_login_at` AS `last_login_at`,  `e`.`password_changed_at` AS `password_changed_at`,  `e`.`password_expire_at` AS `password_expire_at`,  `e`.`two_factor_enabled_yn` AS `two_factor_enabled_yn`,  `e`.`two_factor_secret` AS `two_factor_secret`,  `e`.`ip_whitelist` AS `ip_whitelist`,  `e`.`allowed_time_from` AS `allowed_time_from`,  `e`.`allowed_time_to` AS `allowed_time_to`,  `e`.`role_ids` AS `role_ids`,  'ROLE_EMPLOYEE' AS `role_codes`,  `e`.`group_ids` AS `group_ids`,  `e`.`department_id` AS `department_id`,  `e`.`department_name` AS `department_name`,  `e`.`delete_yn` AS `delete_yn`  from `tb_employee` `e`  where `e`.`delete_yn` = 'N' union all select  'STAFF' AS `user_type`,  `a`.`admin_id` AS `user_id`,  `a`.`admin_seq` AS `uniq_id`,  NULL AS `site_id`,  `a`.`admin_group_id` AS `group_id`,  `a`.`login_id` AS `login_id`,  `a`.`PASSWORD` AS `password`,  `a`.`STATUS` AS `status`,  `a`.`login_fail_count` AS `login_fail_count`,  `a`.`locked_until` AS `locked_until`,  `a`.`last_login_at` AS `last_login_at`,  `a`.`password_changed_at` AS `password_changed_at`,  `a`.`password_expire_at` AS `password_expire_at`,  `a`.`two_factor_enabled_yn` AS `two_factor_enabled_yn`,  `a`.`two_factor_secret` AS `two_factor_secret`,  `a`.`ip_whitelist` AS `ip_whitelist`,  `a`.`allowed_time_from` AS `allowed_time_from`,  `a`.`allowed_time_to` AS `allowed_time_to`,  `a`.`role_ids` AS `role_ids`,  `a`.`role_codes` AS `role_codes`,  `a`.`group_ids` AS `group_ids`,  `a`.`department_id` AS `department_id`,  `a`.`department_name` AS `department_name`,  `a`.`delete_yn` AS `delete_yn`  from `tb_admin` `a`  where `a`.`delete_yn` = 'N';
