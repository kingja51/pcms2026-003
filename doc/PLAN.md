# PLAN — PCMS 2026-003

> 페이즈별 작업 목록 · 완료 기준(DoD) · 진행 추적.
> 규약·아키텍처는 [개발가이드.md](개발가이드.md), 작업 지침은 [CLAUDE.md](../CLAUDE.md).

---

## 1. 목표와 범위

**eGovFrame 5.0 호환 멀티사이트 웹 CMS 를 플랜 주도로 재구축한다.**

`pcms2026-001`(v0.1.0, 동작하는 참조 구현)의 검증된 자산을 **페이즈 순서에 맞춰 선별 이식**한다.
백지에서 새로 쓰는 것이 아니라, **이식 대상과 순서를 플랜이 통제**하는 것이 001·002와의 차이다.

> **참조 우선순위**: ① [개발가이드.md](개발가이드.md) → ② `pcms2026-001` 실측 코드·DDL → ③ `gopcms2026` 원본.
> **`pcms2026-002` 는 코드·문서 모두 참조하지 않는다**(정리되지 않은 상태로 폐기).

### 최종 DoD

1. `./mvnw -o package` war 빌드 성공 + 로컬 임베디드 기동 200
2. 3주체(관리자·회원·익명) 로그인 및 콘텐츠·게시판·파일·검색 동작
3. KRDS 디자인 시스템 적용 — 인라인 핸들러 0건, raw hex 0건 (grep 검증)
4. ArchUnit 규약 게이트 전건 통과
5. 로깅 3종·배치 정상 가동

---

## 2. 진행 규칙

1. **작업 시작 전 현재 페이즈를 확인**하고, 완료 시 체크박스를 갱신한다.
2. **이 플랜에 없는 작업은 하지 않는다.** 필요하면 항목을 먼저 추가하고 승인받는다.
3. 각 페이즈의 **"범위 밖"** 은 그 페이즈에서 **의도적으로 하지 않는 것**이다.
   작업 중 발견한 문제가 범위 밖이면 **§7 발견 사항**에 기록만 하고 손대지 않는다.
4. **한 커밋 = 한 주제.** 페이즈 완료 시 커밋하고, 대량 변경 직전에는 되돌릴 지점을 먼저 확보한다.
5. 페이즈 DoD 를 통과하지 못하면 **다음 페이즈로 넘어가지 않는다.**
6. 이식할 때는 **001 실측 코드를 그대로** 가져온다. "개선"은 별도 항목으로 분리한다 —
   이식과 리팩터링을 동시에 하면 문제 원인을 가릴 수 없다.

---

## 3. 마일스톤 요약

| 페이즈 | 내용 | 산출물 | 상태 |
|---|---|---|---|
| **P0** | 프로젝트 골격 — 빌드·3DB·기동·Flyway | 기동되는 빈 앱 | ⬜ |
| **P1** | 공통 기반 계층 + ArchUnit 게이트 | `common/` 전체 | ⬜ |
| **P2** | 보안·인증 기반 | 로그인·인가 동작 | ⬜ |
| **P3** | 프런트 공통 기반 — KRDS·JS 규약·에디터 | 레이아웃·에디터 | ⬜ |
| **P4** | 핵심 CMS — 사이트·메뉴·콘텐츠·게시판·파일 | CMS 본체 | ⬜ |
| **P5** | 회원 · 인증 연동 | 회원 생명주기 | ⬜ |
| **P6** | 부가 도메인 | 검색·설문·민원 등 | ⬜ |
| **P7** | 운영 · 관측 | 로깅·통계·배치 | ⬜ |
| **P8** | 멀티사이트 데모 · 마감 | 배포 가능 상태 | ⬜ |

---

## 4. 페이즈별 작업

### P0 — 프로젝트 골격

**목표**: 빈 프로젝트가 컴파일되고, 3개 DB에 붙고, 로컬에서 뜬다.

**선행 결정**: D1(Flyway 계정), D2(Flyway 범위), D3(git 원격), D4(DB·데이터 경로) — §6

#### 작업

- [x] `git init` + `.gitignore` — `.env`, `target/`, `node_modules/`, `.idea/`, 빌드 산출물
- [ ] 빌드 정의 이식 — `pom.xml`(artifactId/name/finalName `pcms2026-003`), `lombok.config`, `package.json`, `mvnw`, `.mvn/`
- [ ] `lib/` 로컬 의존 jar 배치(NiceID 등) + pom system-scope 확인
- [ ] `.env.example` 작성 — **키 이름 + `__CHANGE_ME__`**. 비밀 아닌 값(경로·드라이버·localhost URL)만 예시값
- [ ] `application.yml` + `application-{local,dev,prod}.yml` 이식
      — 한글 주석(운영 정책) 보존, **비밀값은 기본값 없는 `${VAR}`**
- [ ] `logback-spring.xml` 이식
- [ ] **3-DB 구성** — `config/datasource/`: `GopcmsDataSourceProperties`, `DataSourceFactory`,
      `PrimaryDataSourceConfig`, `SecondaryDataSourceConfig`, `LoggingDataSourceConfig`
- [ ] **MyBatis 구성** — `config/mybatis/`: `MyBatisDefaults`, DB별 `*MyBatisConfig`(SqlSessionFactory·매퍼 스캔·드라이버별 XML 경로)
- [ ] 진입점 — `GopcmsApplication`(`main()` + `SpringBootServletInitializer` 이중 진입점)
- [ ] eGovFrame 설정(`config/egov/`) 이식
- [ ] **Flyway 도입** — `db/migration/{db}/{vendor}/` 구조, DataSource별 Flyway 빈, `baselineOnMigrate`
- [ ] 로컬 DB 3종 생성 + 001 DDL 로 스키마 구축 → Flyway 베이스라인 기록

#### DoD

- `./mvnw -o compile -DskipTests -Dtailwind.skip=true` **BUILD SUCCESS**
- 로컬 기동 성공 · **HikariPool 3개** 기동 로그(`HikariPrimary`/`HikariSecondary`/`HikariLogging`)
- `/actuator/health` **UP**
- 환경변수 미주입 시 **fail-fast 부팅 실패** 확인
- `flyway_schema_history` 에 베이스라인 행 생성 확인
- `git status` 에 `.env` 가 나타나지 않음

**범위 밖** — 도메인 로직, 화면, 보안 체인, 스케줄러, 매퍼 XML. **기동만 확인한다.**

---

### P1 — 공통 기반 계층

**목표**: 모든 도메인이 의존하는 `common` 을 먼저 세우고, 규약 게이트를 건다.

#### 작업

- [ ] `common/base` — `BaseEntity`, `SoftDeletable`, `UseFlagged`
- [ ] `common/util` — `UuidV7Generator`, `MaskUtils`, `IpUtils`(XFF 우측 스캔), `IpMatcher`,
      `JsonUtils`, `HtmlSafeJson`, `XssSanitizer`, `SafeReplaceUtils`, `SensitiveParamMasker`,
      `RandomPasswordGenerator`, `Fmt`, `CsvUtils`, `QrCodeGenerator`
- [ ] `common/dto` — `PageRequest`(unbounded 지원 포함), `PageResponse`, `ApiResponse`, `ExcelDownloadRequest`
- [ ] `common/crypto` — `AesGcmCipher`, `@Encrypt`, `PiiCryptoProperties`, `EmailHasher`
- [ ] `config/interceptor/EncryptInterceptor` — MyBatis PII 투명 암복호(**DTO 오염 복원·필드단위 복호화 격리** 포함)
- [ ] `common/audit` — `AuditLogger`, `AuditContext`, `AuditEvent`, `PrivacyAccess`+Aspect
- [ ] `config/interceptor/AuditInterceptor` — 감사컬럼 6종 자동 주입
- [ ] `config/filter/AuditContextFilter` — 요청 컨텍스트(사용자·IP) 전파
- [ ] `common/file` — 스토리지(**경로 containment 검사**), 업로드 검증 골격
- [ ] `common/html/HtmlSanitizer` — OWASP Sanitizer 래퍼
- [ ] `common/validator` — `PasswordPolicy`, `PasswordPolicyValidator`
- [ ] `common/security/SecurityContextHelper`, `common/web/StaticResourcePaths`
- [ ] `config/cache` — 캐시 매니저
- [ ] **ArchUnit 게이트 신설** — 개발가이드 §4-4 R1~R6, 예외마다 주석에 이유 명시

#### DoD

- `./mvnw test -Dtest=ArchitectureTest` **전건 통과**
- 암호화 왕복 확인 — 평문 → `{AG}` 프리픽스 암호문 저장 → 복호화 읽기
- 경로 조작 입력(`../`) 차단 확인
- 마스킹 결과 확인(surrogate 문자 포함 문자열에서 깨지지 않을 것)

**범위 밖** — 도메인 서비스, 화면, 보안 체인. **`common` 만 세운다.**

---

### P2 — 보안 · 인증 기반

**목표**: 인증·인가 체계가 서고, 무매칭 DENY 가 실제로 동작한다.

#### 작업

- [ ] `config/security/SecurityConfig` — **다중 SecurityFilterChain**(admin 10 / member 20 / default 100)
- [ ] `SecurityProperties` + `SecurityPropertiesConfig` — 정책 외부화
- [ ] 세션 — `PCMS_SID`, `changeSessionId()`, `maximumSessions(1)`
- [ ] **정적 자원 permitAll** — `/css/**`, `/js/**`, **`/fonts/**`**, `/img/**`, `/tmpl/**`
      (001에서 `/fonts/**` 누락으로 폰트가 조용히 폴백된 이력 — 반드시 포함)
- [ ] `config/access/` — `tb_role_url_access` + `DynamicAuthorizationManager`(priority ASC, **무매칭 DENY**)
- [ ] `PublicEndpoint` / `PublicEndpointRegistry` — 공개 엔드포인트 선언
- [ ] 관리자 로그인 — `primary/system/login`, 2FA(secret **암호화 저장**), `AdminLoginIpGateFilter`
- [ ] `TwoFactorEnforcementInterceptor`
- [ ] 로그인 잠금(5회/30분), `config/filter/RateLimitFilter`(Bucket4j)
- [ ] `LoginFormatValidationFilter`, `SuspiciousRequestFilter`, `HttpFirewallConfig`, `TrustedProxiesConfig`
- [ ] `config/filter/CspNonceFilter` — nonce 발급 + CSP 헤더(개발가이드 §9-2), HTML `no-store`
- [ ] `CspReportController` — 위반 리포트 수집
- [ ] CSRF — 쿠키 방식 + htmx 헤더 주입
- [ ] `AuthExceptionHandlers` — **htmx 요청 분기**(미인증 `HX-Redirect`+401, 인가거부 `HX-Reswap:none`)
- [ ] `tb_role_url_access` 기본 규칙 시드(참조 데이터 → 마이그레이션 포함)

#### DoD

- 관리자 로그인 → 2FA → 관리자 화면 진입
- **접근 규칙 없는 URL 이 DENY** 되는 것 확인
- 응답 헤더에 CSP nonce 확인 · `script-src` 에 `'unsafe-inline'` **없음** 확인
- 로그인 5회 실패 시 잠금 · 잠금 해제 동작
- 2FA secret 이 DB에 `{AG}` 암호문으로 저장되는 것 확인
- 정적 자원이 **익명으로 200** (302 아님)

**범위 밖** — 회원 가입/소셜 로그인/본인인증(P5), 도메인 화면.

---

### P3 — 프런트 공통 기반

**목표**: 이후 모든 화면이 올라탈 레이아웃·JS 규약·에디터를 먼저 확정한다.

**선행 결정**: D5(Namo 패키지) — 미확보여도 진행 가능(폴백 확인으로 대체)

#### 작업

- [ ] `src/krds.css` — KRDS 토큰 원본(`--color-*`, `--radius-*`, `--shadow-*`, 타이포 스케일)
- [ ] Tailwind v4 CLI 빌드 파이프라인 — `npm run css`, maven `generate-resources` 연동
- [ ] self-host 폰트 배치 + `@font-face` (P2의 `/fonts/**` permitAll 과 함께 **200 확인**)
- [ ] `fragments/layout-admin.html` — 관리자 셸(GNB·breadcrumb·flash·스크립트 슬롯)
- [ ] `fragments/layout-front.html` — 사용자 셸
- [ ] 공통 조각 — breadcrumb, 페이지네이션(`aria-current`), flash-alert, 파일 picker, 사이트 푸터
- [ ] `static/js/app.js` — **이벤트 위임 규약 전체**(개발가이드 §9-2 표):
      `data-confirm`(제출버튼 포함·`\n` 처리), `submit-on-change`, `data-history-back`,
      `print`, `dialog-open/close`, 이미지 폴백 3종, `toggle-contrast`, htmx CSRF 주입,
      `role="button"` 키보드 활성화
- [ ] 테마 — `theme-*` 스왑, 고대비(`hc`) 토글 + FOUC-free 복원 부트스트랩
- [ ] **위지윅 에디터 공통 모듈** — 코어(어댑터 레지스트리·값동기화·지연로드·폴백)
      + tiptap 어댑터 + Namo 어댑터 + 설정(전역 기본 엔진) + 스킨 CSS
- [ ] 에디터 확인 화면 1개(엔진 3가지 지정 방식 비교)

#### DoD

- 관리자·사용자 레이아웃 렌더 **200**
- **인라인 `on*=` 핸들러 0건** · **raw hex 0건**(메일 템플릿 제외) — 개발가이드 §15 grep 으로 확인
- self-host 폰트 **woff2 200** 및 실제 적용(시스템 폰트 폴백 아님) 확인
- 에디터: 값이 원본 textarea 로 동기화 · 화면 지정이 전역 기본보다 우선 · **Namo 미반입 시 평문 폴백**
- 고대비 토글 + 새로고침 유지

**범위 밖** — 도메인 화면(P4~), 사이트별 시각 언어 데모(P8).

---

### P4 — 핵심 CMS

**목표**: 사이트·메뉴·콘텐츠·게시판·파일이 동작한다.

#### 작업

- [ ] **사이트/템플릿** — `tb_site`, `tb_template`. siteCode 해석 원칙(**"URL이 진실, 세션은 편의"**),
      `SiteContext` 캐시 + 변경 이벤트로 즉시 반영, `tb_site.theme` 연동
- [ ] **메뉴** — `tb_menu` 트리, menuTree 데이터드리븐 렌더
- [ ] **공통코드** — `tb_code_group`, `tb_code`
- [ ] **콘텐츠** — `tb_content` + `tb_content_history`,
      승인 워크플로 **DRAFT→REVIEW→APPROVED→PUBLISHED**(직행 금지), 수정 시 버전 스냅샷, 미리보기
- [ ] **게시판** — `tb_bbs_master` + 유형별 화면(NOTICE/FREE/QNA/GALLERY/PHOTO/YOUTUBE/BODO/FAQ),
      `tb_bbs_article`·`tb_bbs_category`·`tb_bbs_comment`·`tb_bbs_like`·`tb_bbs_report`
- [ ] **파일** — `tb_file`, `tb_file_group`. **업로드 6중 방어**(개발가이드 §10-3),
      다운로드 이력, 미리보기, 파일 그룹 UUID 일관성(생성 폼에서 그룹 재사용)
- [ ] 각 신규 URL 의 `tb_role_url_access` 규칙 등록
- [ ] 매퍼 3벤더 XML 동시 작성

#### DoD

- 콘텐츠 작성 → 승인 4단계 → 게시 → 수정 시 이력 스냅샷 생성
- 게시판 8유형 중 대표 3유형 작성 → 상세 → 댓글 → 좋아요 → 신고
- 파일 방어 시나리오 차단 — 확장자 위조, 경로 조작, 허용 외 확장자
- 배너/팝업 없이도 레이아웃 정상 렌더(빈 컬렉션 안전값)
- ArchUnit 전건 통과 · 매퍼 3벤더 개수 일치 · `${}` 0건

**범위 밖** — 검색 색인(P6), 회원 전용 권한(P5 이후), 통계(P7).

---

### P5 — 회원 · 인증 연동

**목표**: 회원 생명주기가 돈다.

#### 작업

- [ ] `tb_member` + 가입(약관 동의 `tb_member_consent`·유형 선택)·로그인·마이페이지
- [ ] 비밀번호 정책·변경·찾기, `tb_member_password_history`
- [ ] 통합 로그인 `v_user_login` VIEW (반복 마이그레이션 `R__` 로 관리)
- [ ] OAuth2 소셜 로그인 — 네이버·카카오·구글(`tb_member_oauth`), `config/oauth2/`
- [ ] **NICE 본인인증** — `primary/identity`. **JPMS 플래그 필요**
      (surefire · `spring-boot:run` · 운영 setenv **모두**. 누락 시 본인인증에서만 500)
- [ ] 휴면 전환·분리보관 — `tb_member_dormant`, `tb_member_dormant_notice`(사전 안내)
- [ ] **휴면 해제 본인확인 — 실명인증 / 이메일 OTP 택1** (개발가이드 §10-6)
      - [ ] 수단 선택 화면 — 두 경로 진입점, 성공 후 처리는 동일(역이관 + `restored_at`)
      - [ ] **A. 실명인증** — `primary/identity` `NiceCheckService` 연동, **DI 해시(`di_hash`) 대조**
      - [ ] **B. 이메일 OTP** — **`tb_member_otp` 신규**(마이그레이션 + 3벤더 DDL)
            - [ ] 발송 — 6자리 숫자, 유효 5분, **해시 저장**(평문 금지), 재발송 쿨다운 60초·시간당 상한
            - [ ] 수신처는 휴면 스냅샷 이메일 — **입력 이메일 해시가 일치할 때만 발송**
            - [ ] 검증 — **상수 시간 비교**, 시도 5회 초과 시 폐기, 성공 시 **즉시 소비**(1회용)
            - [ ] 메일 템플릿(`common/mail/MailService` + `tb_mail_template`)
            - [ ] 만료·사용 완료 OTP 정리(P7 보존 배치에 항목 추가)
      - [ ] **계정 열거 차단** — 미존재 계정도 동일 응답·동일 소요시간, 발송 성공/실패와 무관하게 동일 화면
      - [ ] 두 경로 Bucket4j 레이트리밋 + `log_security`·`log_privacy_access` 기록
      - [ ] 정책값(자릿수·유효시간·시도제한·쿨다운) `application.yml` 외부화 + 주석
- [ ] 탈퇴 — `tb_member_withdraw`
- [ ] PII 암호화·마스킹 적용 + `log_privacy_access` 기록 지점 배치
- [ ] 관리자 — 회원 관리 화면, 마스킹 노출

> **이식 주의**: 001의 휴면 해제는 실명인증이 아니라 **로그인ID+이름+이메일+비밀번호 3요소 일치**
> (`DormantRestoreForm`)다. 003은 위 2수단 택1로 **대체**하므로 001 코드를 그대로 가져오지 않는다.
> `tb_member_otp` 는 001에 없는 신규 테이블이다.

#### DoD

- 가입 → 로그인 → 마이페이지 → 탈퇴 왕복
- DB에 PII 가 `{AG}` 암호문으로 저장 · 화면에서는 마스킹 노출
- 소셜 로그인 3종 중 최소 1종 왕복
- 개인정보 조회 시 `log_privacy_access` 적재 확인
- **휴면 해제 — 두 수단 각각 왕복**
  - 실명인증 경로: NICE 인증 → DI 해시 일치 → 일반 계정 전환
  - 이메일 OTP 경로: 발송 → 메일 수신 → 코드 입력 → 전환
- **OTP 부정 시나리오 차단 확인**
  - 만료(5분 경과) 코드 거부 · 사용 완료 코드 **재사용 거부**
  - 시도 5회 초과 시 폐기 · 재발송 쿨다운 동작
  - DB `tb_member_otp` 에 **평문 코드가 남지 않음** 확인
  - 미존재 계정 요청 시 존재 계정과 **동일한 응답·화면** 확인

**범위 밖** — 휴면/탈퇴 **배치 자동 실행**(P7에서 스케줄러와 함께), 알림 발송(P6).

---

### P6 — 부가 도메인

**목표**: 나머지 도메인을 채운다.

#### 작업

- [ ] **검색** — `tb_search_index` 색인 훅(도메인 CUD 연동), 하이라이트,
      금지어·동의어·추천어, 재색인, `tb_search_keyword` 수집
      — **저장형 XSS 주의**: 검색어는 `HtmlSafeJson` 경유(001 실제 사고)
      — LIKE 와일드카드(`%`/`_`) 이스케이프
- [ ] **설문** — `tb_survey_master`/`tb_survey`/`question`/`option`/`response`/`answer`,
      중복응답 차단, 집계 화면
- [ ] **민원** — `tb_complaint_master`/`category`/`article`/`answer`, 채번, 상태 전이(RECEIVED→ANSWERED)
- [ ] **일정·휴일** — `tb_schedule_master`, `tb_schedule`, `tb_holiday`, 달력 UI
- [ ] **팝업·배너** — `tb_popup`, `tb_banner`, location별 노출, 캐시 + CUD 시 evict
- [ ] **알림** — `tb_notification`, `tb_notification_pref`, `tb_noti_template`, `tb_noti_send`
- [ ] **메일 템플릿** — `tb_mail_template`, 미리보기(raw hex 허용 구역)
- [ ] 각 신규 URL 접근 규칙 등록

#### DoD

- 각 도메인 CRUD 왕복
- 검색: 글 작성 → 자동 색인 → `/search` 노출(하이라이트) → 관리자 재색인
- 설문: 발행 → 응답 → 중복 차단 → 집계
- 민원: 접수 → 채번 → 답변 → 상태 전이
- ArchUnit 전건 통과

**범위 밖** — 통계 대시보드(P7), 사이트 데모(P8).

---

### P7 — 운영 · 관측

**목표**: 로그가 쌓이고 배치가 돈다.

#### 작업

- [ ] **접속 로그** — `AccessLogFilter` → `log_access`
- [ ] **오류 로그** — `ErrorLoggingExceptionResolver` → `log_error`
- [ ] **보안·로그인 로그** — `log_security`, `log_login`
- [ ] **감사 로그** — `log_audit` (P1 `AuditLogger` 연동 완성)
- [ ] **파일 다운로드 이력** — `log_file_download`
- [ ] **통계** — `stat_access_daily`, `stat_access_uri_daily`, `stat_daily_visit`,
      `stat_content_view`, `stat_search_keyword` + 대시보드
- [ ] **로그 뷰어** — 관리자 조회 화면
- [ ] **ShedLock** — `config/shedlock/`, logging DB `shedlock` 테이블, `defaultLockAtMostFor`
- [ ] **스케줄러 9종** — 개발가이드 §12 표 전체. cron 외부화 + **dry-run 플래그**
- [ ] **보존 정책** — 로그 보존, soft-delete 정리, 파일 퍼지, 탈퇴 파기(`tb_pii_purge_log`),
      **만료·사용 완료 OTP 정리**(`tb_member_otp` — P5에서 도입)
- [ ] Actuator — 공개는 `health`·`info`·`prometheus` 만, 나머지 `ROLE_ADMIN`

#### DoD

- 접속 로그 적재 → 집계 배치 실행 → `stat_*` 반영 확인
- 배치 **dry-run 발화** + ShedLock 락 행 생성 확인
- 로그 기록이 본 트랜잭션 롤백과 무관하게 남는 것 확인(`REQUIRES_NEW`)
- Actuator 비공개 엔드포인트가 익명에 차단

**범위 밖** — 외부 모니터링(APM·알림) 연동.

---

### P8 — 멀티사이트 데모 · 마감

**목표**: 배포 가능 상태로 마무리한다.

**선행 결정**: D6(데모 시각 언어)

#### 작업

- [ ] 사이트 데모 **5종 세트** — 한 세트 = **레이아웃 + 콘텐츠 + 컨트롤러 + `tb_role_url_access` + 시드**
      (레이아웃만 만들고 시드·접근 규칙을 빠뜨리는 것이 **가장 흔한 실수**)
- [ ] 사이트 추가 절차 문서화(복제 레시피)
- [ ] 배포 자산 — `deploy/tomcat/setenv.sh`·`setenv.bat`·`pcms.env.example`
      (**NICE JPMS 플래그** 포함, 비밀값은 스크립트 밖 `conf/pcms.env`)
- [ ] war 표준 패키징 확인(repackage 안 함) + 외부 Tomcat 배포 리허설
- [ ] 문서 최종 갱신 — README·개발가이드·PLAN
- [ ] 전체 회귀 확인

#### DoD

- **최종 DoD(§1) 5개 항목 전부 통과**
- 데모 사이트 전 페이지 익명 200
- war 가 외부 Tomcat 에서 기동

---

## 5. 상시 게이트 (모든 페이즈 공통)

1. 작업 후 `./mvnw -o compile` 통과
2. 도메인 추가 시 `./mvnw test -Dtest=ArchitectureTest` 통과
3. **신규 URL 추가 시 `tb_role_url_access` 규칙 등록**
4. **매퍼 수정 시 3벤더 XML 동기 수정** · `${}` 0건
5. 화면 작업 시 **인라인 핸들러 0건 · raw hex 0건**(개발가이드 §15 grep)
6. **커밋 전 `.env` 스테이징 여부 확인**
7. 페이즈 완료 = 체크박스 갱신 + 커밋
8. 적용된 Flyway 마이그레이션 파일은 **수정하지 않는다**(새 버전 추가)

---

## 6. 결정 대기

> 해당 페이즈 시작 전에 확정한다. 결정되면 이 표에 결과와 날짜를 적는다.

| # | 항목 | 선택지 | 필요 시점 | 결정 |
|---|---|---|---|---|
| D1 | **Flyway 실행 계정** — 앱 계정에 DDL 권한이 없는 기존 방침과 충돌 | ① DDL 권한 전용 flyway 계정 분리 ② 앱 계정에 DDL 부여 ③ 로컬·dev 만 활성, 운영은 DBA 집행 | **P0** | |
| D2 | **Flyway 적용 범위** | ① 3개 DB 전부 ② primary 만 | **P0** | |
| D3 | **git 원격 저장소** — 사용 여부 및 URL | | **P0** | ✅ 2026-07-31 — `https://github.com/kingja51/pcms2026-003` (public). 기본 브랜치 `main` |
| D4 | **로컬 DB·데이터 경로** — 001과 공유할지 분리할지(스키마명, 업로드/로그 경로) | | **P0** | ✅ 2026-07-31 — 001과 **분리**. MariaDB 11.8.3 `pcms2026-003-{primary,logging,secondary}` (업로드·로그 경로 미정) |
| D5 | **Namo CrossEditor 4** 납품 패키지 확보 시점 및 제품 API 확인 | | P3 | |
| D6 | 사이트 데모 시각 언어(KRDS / IBM Carbon / 기타) | | P8 | |

---

## 7. 발견 사항 (범위 밖 이슈 기록)

> 작업 중 발견했으나 현재 페이즈 범위가 아닌 것을 **고치지 말고 여기 기록**한다.
> 처리 여부는 사용자가 정하고, 처리하기로 하면 해당 페이즈에 항목으로 추가한다.

| 발견일 | 내용 | 발견 페이즈 | 심각도 | 처리 |
|---|---|---|---|---|
| | | | | |

---

## 8. 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| **범위 확대**(001·002 재발) | 일정 지연·되돌리기 | §2 진행 규칙, 페이즈별 "범위 밖" 명시, §7 발견 사항 기록 |
| **인라인 핸들러가 CSP에 조용히 차단** | 확인창 없이 삭제 실행 등 무증상 결함 | P3에서 위임 규약 확정, DoD에 grep 검사 |
| **매퍼 3벤더 비동기화** | 벤더 전환 시 즉시 장애 | 상시 게이트 4 |
| **신규 URL 접근 규칙 누락** | 화면이 열리지 않음 | 상시 게이트 3 |
| **정적 자원 permitAll 누락** | 폰트·CSS 가 조용히 폴백 | P2 작업 항목에 명시, P3 DoD에서 200 확인 |
| **비밀값 커밋** | 자격증명 유출 | `.env.example` 템플릿 원칙, 상시 게이트 6 |
| **Flyway DDL 권한 정책 충돌** | 운영 적용 불가 | D1 조기 확정 |
| **이식과 리팩터링 혼재** | 문제 원인 추적 불가 | §2-6 — 이식은 실측 그대로, 개선은 별도 항목 |
| 테스트가 얇음 | 회귀 탐지 지연 | ArchUnit + grep 게이트를 자동 검증의 축으로 |
