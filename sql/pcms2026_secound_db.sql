

USE `pcms2026-003-secondary`;

/*Table structure for table `tb_dept_master` */

DROP TABLE IF EXISTS `tb_dept_master`;

CREATE TABLE `tb_dept_master` (
  `dept_id` varchar(40) NOT NULL COMMENT '학과 ID (UUID v7)',
  `site_id` varchar(40) DEFAULT NULL COMMENT '학과 사이트 ID (tb_site.site_id)',
  `site_code` varchar(30) DEFAULT NULL COMMENT '학과 사이트 코드 (예: me)',
  `dept_code` varchar(40) NOT NULL COMMENT '학과코드 (예: ME)',
  `dept_name` varchar(120) NOT NULL COMMENT '학과명',
  `dept_name_en` varchar(200) DEFAULT NULL COMMENT '학과명(영문)',
  `college` varchar(120) DEFAULT NULL COMMENT '단과대학/계열 (트리 부모명 denormalized)',
  `campus` varchar(60) DEFAULT NULL COMMENT '캠퍼스(메디컬/글로컬 등, 트리 조상 denormalized)',
  `parent_dept_id` varchar(40) DEFAULT NULL COMMENT '상위 노드 dept_id (tb_menu 의 parent_menu_id 처럼 self-tree, NULL=최상위)',
  `node_type` varchar(20) NOT NULL DEFAULT 'DEPT' COMMENT '노드 유형 CAMPUS/COLLEGE/DEPT/MAJOR',
  `depth` int(11) NOT NULL DEFAULT 1 COMMENT '계층 depth 1=캠퍼스 2=단과대학/계열 3=학과 4=전공',
  `head_professor` varchar(60) DEFAULT NULL COMMENT '학과장',
  `phone` varchar(40) DEFAULT NULL COMMENT '대표전화',
  `fax` varchar(40) DEFAULT NULL COMMENT '팩스',
  `email` varchar(120) DEFAULT NULL COMMENT '대표이메일',
  `office` varchar(200) DEFAULT NULL COMMENT '사무실/위치',
  `homepage` varchar(255) DEFAULT NULL COMMENT '홈페이지 URL',
  `description` text DEFAULT NULL COMMENT '학과 소개',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`dept_id`),
  UNIQUE KEY `uk_dept_master_code` (`site_code`,`dept_code`),
  KEY `idx_dept_master_site` (`site_code`,`delete_yn`,`use_yn`,`sort_order`),
  KEY `idx_dept_master_tree` (`parent_dept_id`,`delete_yn`,`use_yn`,`sort_order`),
  KEY `idx_dept_master_campus` (`campus`,`node_type`,`delete_yn`,`use_yn`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='학과 관리(마스터) — 캠퍼스▸단과대학▸학과▸전공 self-tree';

/*Table structure for table `tb_election_voter` */
/* 2026-07-31 primary → secondary 이동 (개별프로그램). FK 없음 — 이동에 따른 제약 변경 없음.
   site_id/site_code 는 primary 의 tb_site 를 가리키지만 DB 가 달라 조인 불가 —
   사이트 범위 필터는 애플리케이션에서 SiteContext 로 건다. */

DROP TABLE IF EXISTS `tb_election_voter`;

CREATE TABLE `tb_election_voter` (
  `voter_id` varchar(40) NOT NULL COMMENT '선거인 ID (UUID v7)',
  `voter_seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '선거인 일련번호 (화면 순번)',
  `election_id` varchar(40) DEFAULT NULL COMMENT '선거 ID — tb_election 도입 시 FK',
  `site_id` varchar(40) DEFAULT NULL COMMENT '사이트 ID (멀티사이트 분리, primary tb_site 참조 — DB 분리로 FK 없음)',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='선거인명부 일괄등재 작업';

/*Table structure for table `tb_faculty` */

DROP TABLE IF EXISTS `tb_faculty`;

CREATE TABLE `tb_faculty` (
  `faculty_id` varchar(40) NOT NULL COMMENT '교수 ID (UUID v7)',
  `site_id` varchar(40) DEFAULT NULL COMMENT '학과 사이트 ID (tb_site.site_id)',
  `site_code` varchar(30) NOT NULL COMMENT '학과 사이트 코드 (예: me)',
  `dept_code` varchar(40) DEFAULT NULL COMMENT '학과코드 (tb_dept_master.dept_code)',
  `name_ko` varchar(60) NOT NULL COMMENT '이름(국문)',
  `name_en` varchar(120) DEFAULT NULL COMMENT '이름(영문)',
  `position` varchar(60) DEFAULT NULL COMMENT '직급(교수/부교수/조교수)',
  `office` varchar(120) DEFAULT NULL COMMENT '연구실 위치',
  `email` varchar(120) DEFAULT NULL COMMENT '이메일',
  `phone` varchar(40) DEFAULT NULL COMMENT '전화',
  `website` varchar(255) DEFAULT NULL COMMENT '홈페이지 URL',
  `research_area` varchar(500) DEFAULT NULL COMMENT '연구분야',
  `photo_url` varchar(255) DEFAULT NULL COMMENT '사진 경로/URL',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`faculty_id`),
  KEY `idx_faculty_site` (`site_code`,`delete_yn`,`use_yn`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='학과 교수진(다학과 공용)';

/*Table structure for table `tb_faculty_program` */

DROP TABLE IF EXISTS `tb_faculty_program`;

CREATE TABLE `tb_faculty_program` (
  `program_id` varchar(40) NOT NULL COMMENT '실적 ID (FPG_ + UUID v7)',
  `faculty_id` varchar(40) NOT NULL COMMENT '교수 ID (tb_faculty.faculty_id)',
  `program_type` varchar(20) NOT NULL COMMENT '실적 유형' CHECK (`program_type` in ('JOURNAL','BOOK','PROJECT','ACTIVITY','PATENT','AWARD')),
  `title` varchar(500) NOT NULL COMMENT '명칭 (논문명/저서명/과제명/활동내용/출원등록명/수상명)',
  `organization` varchar(200) DEFAULT NULL COMMENT '기관 (학술지명/출판사/지원기관명/수상기관)',
  `role_desc` varchar(200) DEFAULT NULL COMMENT '활동역할 (ACTIVITY)',
  `reg_no` varchar(60) DEFAULT NULL COMMENT '등록번호 (PATENT, 예: 10-2936704)',
  `category` varchar(20) DEFAULT NULL COMMENT '구분 (PATENT=국내/국외, AWARD=교내/교외)',
  `issue_date` varchar(20) DEFAULT NULL COMMENT '일자 (게재/발행/등록/수상일, 표기 문자열 보존)',
  `begin_date` varchar(20) DEFAULT NULL COMMENT '기간 시작 (연구/활동기간)',
  `end_date` varchar(20) DEFAULT NULL COMMENT '기간 종료 (연구/활동기간)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by` varchar(40) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(40) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at` timestamp NULL DEFAULT current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`program_id`),
  KEY `idx_faculty_program_faculty` (`faculty_id`,`program_type`,`delete_yn`,`use_yn`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='교수 실적 6종(논문/저서/연구과제/학술활동/지식재산권/수상) 단일 테이블';
 