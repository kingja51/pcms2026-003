# PLAN — PCMS 2026-003

> 페이즈별 작업 목록 · 완료 기준(DoD) · 진행 추적.
> 규약·아키텍처는 [개발가이드.md](개발가이드.md), 작업 지침은 [CLAUDE.md](../CLAUDE.md).

---

## 1. 목표와 범위

**eGovFrame 5.0 호환 멀티사이트 웹 CMS 를 플랜 주도로 재구축한다.**

`pcms2026-001`(v0.1.0, 동작하는 참조 구현)의 검증된 자산을 **페이즈 순서에 맞춰 선별 이식**한다.
백지에서 새로 쓰는 것이 아니라, **이식 대상과 순서를 플랜이 통제**하는 것이 001·002와의 차이다.

> **참조 우선순위**: ① [개발가이드.md](개발가이드.md) → ② `pcms2026-001` 실측 코드·DDL → ③ `pcms2026-001` 원본.
> **`pcms2026-002` 는 코드·문서 모두 참조하지 않는다**(정리되지 않은 상태로 폐기).

### 최종 DoD

1. `./mvnw -o package` war 빌드 성공 + 로컬 임베디드 기동 200
2. 3주체(관리자·회원·익명) 로그인 및 콘텐츠·게시판·파일 동작
   (**검색은 범위 밖** — 외부 검색엔진을 `contextPath=/search` 로 분리)
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
7. **이식 시 `Egov` 접두 클래스명은 리네이밍한다.** 호환성 가이드 규칙 6-② —
   실행환경(`org.egovframe.rte`) 클래스를 상속한 클래스는 이름이 `Egov` 로 시작할 수 없고,
   `org.egovframe.rte` 패키지 안에 정의할 수도 없다(공통컴포넌트·개발환경 템플릿 재사용분은 예외).
   001 의 `EgovXxx` 클래스를 그대로 복사해 오면 **호환성 위반**이 된다.

---

## 3. 마일스톤 요약

| 페이즈 | 내용 | 산출물 | 상태 |
|---|---|---|---|
| **P0** | 프로젝트 골격 — 빌드·3DB·기동·Flyway | 기동되는 빈 앱 | ✅ 2026-07-31 |
| **P1** | 공통 기반 계층 + ArchUnit 게이트 | `common/` 전체 | ✅ 2026-07-31 |
| **P2** | 보안·인증 기반 | 로그인·인가 동작 | 🟡 2026-07-31 — 화면(P3) 대기로 부분 |
| **P3** | 프런트 공통 기반 — KRDS·JS 규약·에디터 | 레이아웃·에디터 | ✅ 2026-07-31 |
| **P4** | 핵심 CMS — 사이트·메뉴·콘텐츠·게시판·파일 | CMS 본체 | 🟡 2026-07-31 — 작업 전건 완료, DoD 3건 기동 검증 대기 |
| **P5** | 회원 · 인증 연동 | 회원 생명주기 | 🟡 2026-08-01 — 작업 완료(OTP 정리 배치는 P7), DoD 왕복 검증 대기 |
| **P6** | 부가 도메인 | 설문·민원·일정·팝업·알림 등 | ⬜ |
| **P7** | 운영 · 관측 | 로깅·통계·배치 | ⬜ |
| **P8** | 멀티사이트 데모 · 마감 | 배포 가능 상태 | ⬜ |

---

## 4. 페이즈별 작업

### P0 — 프로젝트 골격

**목표**: 빈 프로젝트가 컴파일되고, 3개 DB에 붙고, 로컬에서 뜬다.

**선행 결정**: D1(Flyway 계정), D2(Flyway 범위), D3(git 원격), D4(DB·데이터 경로) — §6

#### 작업

- [x] `git init` + `.gitignore` — `.env`, `target/`, `node_modules/`, `.idea/`, 빌드 산출물
- [x] **`pom.xml` 이식** — artifactId/name/finalName `pcms2026-003`, war, Java 21, Spring Boot 3.5.9 (2026-07-31)
- [x] 나머지 빌드 정의 이식 — `lombok.config`, `package.json`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` (2026-07-31)
      — `package.json` 은 `css`·`css:watch` 2개만 이식. 001 의 `css:tmpl`·`css:sg`·`css:all` 은
        `scripts/*.mjs`·`styleguide/` 의존이라 003 에 해당 자산이 없어 제외
      — `.gitignore` 의 Tailwind 산출물 경로를 `app.css` → **`output.css`** 로 정정(실제 빌드 산출물명)
- [x] **eGov 호환성 — 실행환경 필수 4종 의존성 명시** (호환성 가이드 규칙 2-①)
      `egovframe-rte-ptl-mvc` · `-fdl-cmmn` · `-psl-dataaccess` · `-fdl-logging` **전부 5.0.0** + 선택 `-fdl-idgnr` 동일 버전.
      **001 은 `fdl-logging` 을 통째로 exclusion 해 규칙 2 위반이었다** — 003 은 모듈을 살리고
      `log4j-core`/`log4j-slf4j2-impl` 만 제외(`log4j-api` 는 유지 → Spring Boot `log4j-to-slf4j` 가 Logback 으로 위임)
- [x] **eGov 호환성 — Spring Boot 버전 상한 고정** — 기준선 **3.5.6**, 현 지정 **3.5.9**(패치 상향, 규칙 2 예외조항).
      **3.6.x/4.x minor 상향은 즉시 위반** — pom 헤더 주석에 명시함. Java 21 은 "JDK 17 이상" 충족
- [x] **`dependency:tree` 실측 검증 완료** (2026-07-31, 아티팩트 225개)
      — rte **5종 전부 5.0.0** · Spring Boot **3.5.9** · spring-core **6.2.15** · spring-security-core **6.5.7** ·
        mybatis **3.5.19**(기준선 일치) · Flyway 11.7.2(core+mysql)
      — **log4j 브리지 단일화 확인**: `log4j-to-slf4j` + `log4j-api` 만 존재,
        `log4j-slf4j2-impl`·`log4j-core` **0건** → 001 의 부팅 실패 조건 해소
      — postgresql · lucene · genai **0건**
- [x] **`com.google.genai:google-genai` 이식 제외 완료** (2026-07-31) — property 포함 미반입.
      `lucene-analysis-nori`·`postgresql`·`testcontainers-postgresql` 도 함께 제외.
      AI 기능은 추후 context 방식으로 별도 개발한다
- [x] `lib/` 로컬 의존 jar 배치(NiceID 등) + pom system-scope 확인 (2026-07-31, 사용자 배치)
- [x] `.env.example` 작성 — **키 이름 + `__CHANGE_ME__`**. 비밀 아닌 값(경로·드라이버·localhost URL)만 예시값
      — 001 실측 57종 전량. 키 인벤토리는 `.env.key.example`(용도·발급처·페이즈·[S]/[P] 구분) 로 분리
- [x] `application.yml` + `application-{local,dev,prod}.yml` 이식 (2026-07-31)
      — 한글 주석(운영 정책) 보존, **비밀값 평문 fallback 전량 제거**
      — 포트 분리: base **8083** / local **8084** (001 은 8081/8080 — 동시 기동 가능)
        ※ local 은 8082 로 잡았다가 개발 PC 의 다른 java 프로세스와 충돌해 8084 로 변경(실측)
      — `gopcms.flyway.*` 신설(local 만 기본 활성, dev/prod 는 D1 결정 전까지 false)
      — 제거: `ai.gemini.*` 전체 · `gopcms.search.stopwords` · retention buckets 의 `tb_search_*` 4종
      — `g2b` 블록 전체 제거 (tb_g2b_* 4종 미이식 확정 — 2026-07-31)
- [x] `.env.example` 키 정합 (2026-07-31) — yml 요구 58종 = 템플릿 58종.
      `PCMS_MAIL_{HOST,PORT,USERNAME}`·`PCMS_OAUTH2_*_ENABLED`·`PCMS_FLYWAY_ENABLED`·
      `PCMS_CONTENT_HTML_ROOT` 추가, `GEMINI_*` 6종·`PCMS_SEARCH_STOPWORDS_ENABLED` 삭제
- [x] `logback-spring.xml` 이식 (2026-07-31) — STDOUT/FILE/AUDIT_JSON/SQL 4채널.
      001 의 `${/data/gopcms2026/logs}` 오타(logback 미정의 키 → `..._IS_UNDEFINED` 디렉터리 생성)를 `:-` 기본값 문법으로 교정
- [x] **3-DB 구성** — `config/datasource/` 5개 클래스 (2026-07-31). 기동 시 **HikariPool 3개 생성 확인**
- [x] **MyBatis 구성** — `config/mybatis/` 4개 (2026-07-31).
      **`MapperConfigurer` + `@EgovMapper`**(호환성 규칙 5) — 001 의 `@MapperScan`+`@Mapper` 를 대체.
      `annotationClass=EgovMapper.class` 필터로 basePackage 를 도메인별 열거 없이 광역 지정.
      `MapperConfigurer` 는 `static @Bean`(BeanDefinitionRegistryPostProcessor), 팩토리는 빈 이름으로 연결
- [x] **eGov 호환성 — 매퍼 스캔 `MapperConfigurer` 전환 완료** (2026-07-31)
      FQN 을 5.0 jar 로 **실측 확인**: `org.egovframe.rte.psl.dataaccess.mapper.MapperConfigurer`
      (`MapperScannerConfigurer` 상속) · `...mapper.EgovMapper`(`value()` 보유 애노테이션).
      기동 로그에서 3개 DataSource 각각 스캔 동작 확인
- [x] 진입점 — **`com.gonet.Pcms2026Application`**(`main()` + `SpringBootServletInitializer` 이중 진입점),
      `DataSourceAutoConfiguration` 제외 + `@EnableCaching/@EnableAsync/@EnableScheduling/@EnableTransactionManagement` (2026-07-31)
- [x] eGovFrame 설정 `config/egov/RteCommonConfig` 이식 (2026-07-31) — `leaveaTrace` 빈.
      `EgovAbstractServiceImpl` 이 이름으로 주입받으므로 빈 이름 고정 필수.
      클래스명은 `EgovCommonConfig` → **`RteCommonConfig`** 로 변경 — 규칙 7 대상은 아니지만
      이름 기반 점검에서 매번 걸려 해명이 필요해지므로 접두어를 피했다
- [x] **Flyway 도입** (2026-07-31) — `config/flyway/` 2개(`FlywayConfig`, `GopcmsFlywayProperties`),
      `db/migration/{primary,secondary,logging}/mariadb/` 생성, DataSource별 빈 3개.
      `enabled=false` 면 `migrate()` 호출 없이 빈만 만든다(운영 = DBA CLI 집행, D1 ③)
- [x] **로컬 DB 3종 스키마 구축 완료** (2026-07-31, MariaDB 11.8.3 localhost:3306)
      — primary **62 테이블 + 4 뷰 + FK 42**, secondary **5**, logging **12**
      — DDL 3종 **멱등 확인**(반복 실행 exit=0, 객체 수 불변)
      — FK 42개 전부 실재 테이블 참조 확인(`FOREIGN_KEY_CHECKS=0` 로 생성했으므로 필수 검증)
      — `@@foreign_key_checks` 세션 원복(=1) 확인
- [x] **Flyway 베이스라인 기록 완료** (2026-07-31) — 3개 DB 전부 `flyway_schema_history` 에
      `version=0 / << Flyway Baseline >> / BASELINE / success=1` 1행씩 생성 확인

#### DoD

- `./mvnw -o compile -DskipTests -Dtailwind.skip=true` **BUILD SUCCESS**
- 로컬 기동 성공 · **HikariPool 3개** 기동 로그(`HikariPrimary`/`HikariSecondary`/`HikariLogging`)
- `/actuator/health` **UP**
- 환경변수 미주입 시 **fail-fast 부팅 실패** — ✅ `config/env/RequiredPropertyValidator` 로 해결(2026-07-31).
  yml 원본 값의 미해결 `${...}` 를 스스로 찾아 중단한다 — 필수 키 목록을 코드에 두지 않는다
- `flyway_schema_history` 에 베이스라인 행 생성 확인
- `git status` 에 `.env` 가 나타나지 않음
- **eGov 호환성** — `./mvnw dependency:tree` 로 확인:
  실행환경 4종 **동일 버전(5.0.0)** · Spring Boot **3.5.x** · Spring Framework **6.2.x**
- **eGov 호환성** — 매퍼 스캔이 `MapperConfigurer` 경유(3개 DataSource 각각)이고
  매퍼 인터페이스에 `@EgovMapper` 가 붙는 것 확인

**범위 밖** — 도메인 로직, 화면, 보안 체인, 스케줄러, 매퍼 XML. **기동만 확인한다.**

---

### P1 — 공통 기반 계층

**목표**: 모든 도메인이 의존하는 `common` 을 먼저 세우고, 규약 게이트를 건다.

#### 작업

- [x] `common/base` — `BaseEntity`, `SoftDeletable`, `UseFlagged` (2026-07-31)
- [x] `common/util` 13종 (2026-07-31) — `UuidV7Generator`·`MaskUtils`·`IpUtils`·`IpMatcher`·`JsonUtils`·
      `HtmlSafeJson`·`XssSanitizer`·`SafeReplaceUtils`·`SensitiveParamMasker`·`RandomPasswordGenerator`·
      `Fmt`·`CsvUtils`·`QrCodeGenerator`
- [x] `common/dto` 4종 (2026-07-31)
- [x] `common/crypto` 4종 + **D11 적용** (2026-07-31) — `PiiCryptoProperties.hmacKey` 추가,
      `EmailHasher` 가 `getHmacKey()` 사용. 단위 테스트로 키 분리 확인(같은 입력·다른 키 → 다른 해시)
- [x] `config/interceptor/EncryptInterceptor` (2026-07-31)
- [x] `common/audit` 6종 (2026-07-31) — `AuditSpringEvent` 포함.
      의존하는 `logging` 측 8파일(`AuditLog`·`AuditLogMapper`·privacy dto/mapper/service·`LogSearch`)도 함께 이식.
      매퍼 2종은 **`@EgovMapper` 로 전환**(호환성 규칙 5). XML·보존정책은 P7
- [x] `config/interceptor/AuditInterceptor` (2026-07-31)
- [x] `config/filter/AuditContextFilter` (2026-07-31)
- [x] `common/file` 골격 13종 (2026-07-31) — `config`/`dto`/`security` 3개 서브패키지.
      `FileStorage.resolveWithin` 의 `normalize()`+`startsWith()` containment 검사 단위 테스트로 확인.
      `service/` 6종(업로드·다운로드·문서변환)은 **P4**
- [x] `common/html/HtmlSanitizer` (2026-07-31)
- [x] `common/validator` 2종 (2026-07-31)
- [x] `common/security/SecurityContextHelper`, `common/web/StaticResourcePaths` (2026-07-31)
      — `SecurityContextHelper` 가 `CustomUserDetails` 에 의존해 `primary/system/login/dto` 3종을 함께 이식.
        R6 이 `common → primary.dto` 를 허용하므로 규약 위반이 아니다
- [x] `config/cache` — `CacheConfig`, `CacheType` (2026-07-31)
- [x] **ArchUnit 게이트 신설** (2026-07-31) — **10 규칙 전건 통과**.
      R1·R2·R3·R4a·R4b·R5a·R5b·R5c·R6 + **R7 신설**(호환성 규칙 7 — rte 상속 클래스 `Egov` 접두 금지).
      R4b 는 `@Mapper` 사용 0건을 강제한다.
      **001 의 R3 예외 2건(모니터링·Gemini)은 이식하지 않았다** — 호환성 규칙 4 는 예외 없음
- [x] **eGov 호환성 — R3 예외 0건으로 신설** (2026-07-31) — 001 의 예외를 가져오지 않았다
- [x] **eGov 호환성 — R4 를 `@EgovMapper` 기준으로 작성** (2026-07-31). R4b 로 `@Mapper` 0건 강제

#### DoD

- `./mvnw test -Dtest=ArchitectureTest` **전건 통과** — ✅ 10 규칙 (2026-07-31)
- 암호화 왕복 확인 — 평문 → `{AG}` 프리픽스 암호문 저장 → 복호화 읽기 — ✅
- 경로 조작 입력(`../`) 차단 확인 — ✅
- 마스킹 결과 확인(surrogate 문자 포함 문자열에서 깨지지 않을 것) — ✅
- 검증은 `CommonFoundationTest` 10건으로 자동화 — 전체 `./mvnw test` **20건 통과**

**범위 밖** — 도메인 서비스, 화면, 보안 체인. **`common` 만 세운다.**

---

### P2 — 보안 · 인증 기반

**목표**: 인증·인가 체계가 서고, 무매칭 DENY 가 실제로 동작한다.

#### 작업

- [x] `config/security/SecurityConfig` — **admin(10) + default(100)** (2026-07-31).
      **member(20) 체인은 P5 로 이월** — 핸들러가 `MemberMapper`(로그인 잠금 카운터)에 의존하는데
      회원 도메인이 P5 범위다. 제외해도 `/member/**` 는 default 체인 → 무매칭 DENY 로 안전하다
- [x] `SecurityProperties` + `SecurityPropertiesConfig` (2026-07-31)
- [x] 세션 — `PCMS_SID`, `changeSessionId()`, `maximumSessions(1)` (2026-07-31)
- [ ] **정적 자원 permitAll** — `/css/**`, `/js/**`, **`/fonts/**`**, `/img/**`, `/tmpl/**`
      (001에서 `/fonts/**` 누락으로 폰트가 조용히 폴백된 이력 — 반드시 포함)
- [x] `config/access/DynamicAuthorizationManager` (2026-07-31) — **무매칭 DENY 실측 확인**
      (`/nonexistent-page` → 302). 001 은 `primary/system/access/service` 에 두었으나
      개발가이드 §4-1·PLAN 이 지정한 `config/access/` 로 옮겼다 — §7 기록
- [x] `PublicEndpoint` / `PublicEndpointRegistry` (2026-07-31)
- [x] 관리자 로그인 — `primary/system/login` (2026-07-31). 2FA(`TotpService`·`TwoFactorSession`·
      `TwoFactorUsrController`), `AdminLoginIpGateFilter`. **화면 렌더는 P3**(템플릿 미존재로 현재 500)
- [x] `TwoFactorEnforcementInterceptor` (2026-07-31)
- [x] 로그인 잠금(5회/30분), `config/filter/RateLimitFilter`(Bucket4j) (2026-07-31)
- [x] `LoginFormatValidationFilter`, `SuspiciousRequestFilter`, `HttpFirewallConfig`, `TrustedProxiesConfig` (2026-07-31)
      — `LoginFormatValidationFilter` 의 captcha 확인은 **admin 만**. member 는 P5, employee 는 D7 로 영구 제외
- [x] `config/filter/CspNonceFilter` (2026-07-31) — 실측: `script-src 'self' 'nonce-…' 'strict-dynamic'`,
      **`'unsafe-inline'` 없음 확인**
- [x] `CspReportController` (2026-07-31)
- [x] CSRF — 쿠키 방식 (2026-07-31). htmx 헤더 주입은 **P3**(`app.js`)
- [x] `AuthExceptionHandlers` (2026-07-31)
- [x] **첫 Flyway 마이그레이션** `V2026073101__seed_role_and_url_access.sql` (2026-07-31)
      — `tb_role` 7종(ROLE_EMPLOYEE 제외, D7) + `tb_role_url_access` 6종.
      실기동에서 적용 확인(`Successfully applied 1 migration`), `flyway_schema_history` 2행

#### DoD

- 관리자 로그인 → 2FA → 관리자 화면 진입 — ⏸ **P3 대기**. 백엔드는 완성이나 Thymeleaf 템플릿이
  없어 `/admin/login` 이 500 이다(템플릿은 P3 범위). 화면이 생기면 이 항목만 재검증한다
- **접근 규칙 없는 URL 이 DENY** — ✅ `/nonexistent-page` → 302 (2026-07-31)
- CSP nonce · `script-src` 에 `'unsafe-inline'` **없음** — ✅
  실측 `script-src 'self' 'nonce-…' 'strict-dynamic' 'wasm-unsafe-eval' …`
- 로그인 5회 실패 시 잠금 · 잠금 해제 — ⏸ P3 대기(로그인 폼 필요)
- 2FA secret 이 `{AG}` 암호문으로 저장 — ⏸ P3 대기
- 정적 자원이 **익명으로 200** (302 아님) — ✅ 통과 확인.
  `/css/**`·`/fonts/**` 가 **404**(파일 미생성, Tailwind 빌드는 P3)이고 302 가 아니다 —
  차단됐다면 302 로 로그인 리다이렉트가 떴을 것이다

**범위 밖** — 회원 가입/소셜 로그인/본인인증(P5), 도메인 화면.

---

### P3 — 프런트 공통 기반

**목표**: 이후 모든 화면이 올라탈 레이아웃·JS 규약·에디터를 먼저 확정한다.

**선행 결정**: D5(Namo 패키지) — 미확보여도 진행 가능(폴백 확인으로 대체)

#### 작업

- [x] `src/krds.css` — KRDS 토큰 원본 이식(362줄) + 에디터 스킨 추가 (2026-07-31)
- [x] Tailwind v4 CLI 빌드 파이프라인 (2026-07-31) — `npm run css` → `output.css` 41KB.
      maven `generate-resources` 는 **`npm run build`**(css + editor) 를 호출한다
- [x] self-host 폰트 배치 + `@font-face` (2026-07-31) — Pretendard/PretendardGOV woff2 2종(7.2MB).
      **woff2 200 · `@font-face` 2건 적용 확인** — 001 의 `/fonts/**` 누락 회귀 재현 안 됨
- [x] `fragments/layout-admin.html` — 관리자 셸 (2026-07-31). Thymeleaf Layout Dialect 방식
- [x] `fragments/layout-front.html` — 사용자 셸 (2026-07-31)
- [x] 공통 조각 — breadcrumb·파일 picker·사이트 푸터·captcha·notification-bell (2026-07-31)
      — `site-footer` 는 **재작성**. 001 은 특정 대학 주소·전화·담당자·로고가 하드코딩돼 있었다.
        멀티사이트 CMS 이므로 값은 `tb_site` 에서 오게 하고 데모 표현은 P8 로 미룬다
      — `breadcrumb` 은 `BreadcrumbResolver`(P4 사이트 도메인) 의존이라 P4 이후 렌더된다
      — **페이지네이션 조각은 P4 로 이월**(2026-07-31). 001 에 전용 조각 파일이 없고
        실제 목록 화면이 있어야 형태가 정해진다
      — **flash-alert 는 조각이 아니다**(2026-07-31 확인). 001 실측상 `data-flash-alert="메시지"`
        **속성 계약 + `flash-alert.js`** 로 동작한다(엄격 CSP 라 인라인 `alert()` 불가).
        스크립트는 P3 에서 이식됐고, 쓰는 화면에서 로드하면 된다 — 만들 조각이 없다
- [ ] `static/js/app.js` — **이벤트 위임 규약 전체**(개발가이드 §9-2 표):
      `data-confirm`(제출버튼 포함·`\n` 처리), `submit-on-change`, `data-history-back`,
      `print`, `dialog-open/close`, 이미지 폴백 3종, `toggle-contrast`, htmx CSRF 주입,
      `role="button"` 키보드 활성화
- [x] 테마 — `themeClass` 훅 + 고대비(`hc`) 토글 + FOUC-free 복원 (2026-07-31).
      복원 스니펫은 nonce 인라인 `<script>` — CSP 허용 경로. nonce 치환 실측 확인
- [x] **위지윅 에디터 — tiptap** (2026-07-31). **001 에 에디터 모듈이 없어 신규 개발**
      + tiptap 어댑터 + Namo 어댑터 + 설정(전역 기본 엔진) + 스킨 CSS
- [x] 에디터 확인 화면 — `/admin/system/editor-check` (2026-07-31).
      화면지정/전역기본/미등록(폴백) 3종 비교 + 제출값 되돌림(정화 후)

#### DoD

- 관리자·사용자 레이아웃 렌더 **200** — ✅ `/admin/login`·`/admin/system/editor-check` 200
- **인라인 `on*=` 핸들러 0건** · **raw hex 0건** — ✅ (CDN script 0건도 함께 확인)
- self-host 폰트 **woff2 200** 및 실제 적용 — ✅ 2.0MB 전송 · `@font-face` 2건
- 에디터: 값이 원본 textarea 로 동기화 · 화면 지정이 전역 기본보다 우선 · **미등록 엔진 시 평문 폴백**
  — 🟡 폴백·번들 서빙·DOM 계약은 확인. **툴바 추가 후 브라우저 육안 확인은 미완**
- 고대비 토글 + 새로고침 유지 — ✅ nonce 인라인 스니펫 치환 확인

**범위 밖** — 도메인 화면(P4~), 사이트별 시각 언어 데모(P8),
**페이지네이션 조각**(P4 로 이월).

---

### P4 — 핵심 CMS

**목표**: 사이트·메뉴·콘텐츠·게시판·파일이 동작한다.

#### 작업

- [x] **사이트/템플릿** — `tb_site`, `tb_template`. siteCode 해석 원칙(**"URL이 진실, 세션은 편의"**),
      `SiteContext` 캐시 + 변경 이벤트로 즉시 반영, `tb_site.theme` 연동
- [x] **메뉴** — `tb_menu` 트리, menuTree 데이터드리븐 렌더
- [x] **공통코드** — `tb_code_group`, `tb_code`
- [x] **콘텐츠** — `tb_content` + `tb_content_history`,
      승인 워크플로 **DRAFT→REVIEW→APPROVED→PUBLISHED**(직행 금지), 수정 시 버전 스냅샷, 미리보기
- [x] **게시판** — `tb_bbs_master` + 유형별 화면(NOTICE/FREE/QNA/GALLERY/PHOTO/YOUTUBE/BODO/FAQ),
      `tb_bbs_article`·`tb_bbs_category`·`tb_bbs_comment`·`tb_bbs_like`·`tb_bbs_report`
- [x] **파일** — `tb_file`, `tb_file_group`. **업로드 6중 방어**(개발가이드 §10-3),
      다운로드 이력, 미리보기, 파일 그룹 UUID 일관성(생성 폼에서 그룹 재사용)
- [x] **P3 이월 — 페이지네이션 조각** (2026-07-31) — `fragments/pagination.html`.
      `render(page, baseUrl)` / `renderWindow(page, baseUrl, size)` 2종.
      현재 페이지는 `<a>` 가 아니라 **`aria-current="page"` 인 `<span>`** —
      이동할 곳 없는 링크를 탭 순서에 남기지 않는다
      — 검색조건은 화면마다 나열하지 않는다. `SiteContextModelAdvice` 가 주입하는
        **`pageQuery`**(현재 쿼리스트링에서 `page` 만 빼고 URL 인코딩)를 이어붙인다.
        Thymeleaf 링크식은 파라미터 이름이 리터럴이어야 해 Map 을 펼칠 수 없다 —
        **001 에 공용 조각이 없던 이유가 이것**이다
      — 렌더 테스트 7건 + `pageQuery` 테스트 6건으로 검증(앱 기동 불요)
      — ⚠️ **기존 65개 목록 화면은 아직 전환하지 않았다** — §7 기록
- [x] 각 신규 URL 의 `tb_role_url_access` 규칙 등록 (2026-07-31)
      `V2026073104__seed_url_access_p4.sql` — 14행.
      쓰기 경로(`/bbs/**` POST, 업로드, 좋아요·신고)는 **URL 계층에서도 인증 요구** —
      컨트롤러가 이미 막지만 웹쉘 침해 이력이 있어 방어를 한 겹만 두지 않는다.
      조회 경로(게시판·첨부·배너·`/prg`)는 PERMIT_ALL 이고 세밀 권한
      (`readAuth`·`downloadAuth`·비밀글·본인 글)은 컨트롤러·서비스가 판정한다
      — **사이트별 `/{sc}`·`/{sc}/**` 규칙은 넣지 않았다.** catch-all `/*` 를 넣으면
        미등록 사이트 코드까지 열린다. `tb_site` 행과 함께 등록해야 한다(P8)
- [x] 매퍼 XML 작성 — **`*_maria.xml` 단일**(MariaDB 전용, 개발가이드 §6-4)
- [x] **사용자 진입 컨트롤러** (2026-07-31)
      `DefaultUsrController` — 모든 사이트의 `/{sc}`·`/{sc}/home`·`/{sc}/sitemap`·`/{sc}/{slug}`
      를 단일 컨트롤러로 처리(사이트 추가 시 컨트롤러 복사 없음).
      `ProgramUsrController` — `/prg/{program}/{siteCode}` 학과 프로그램 제네릭 쉘
      — **`AbstractSiteUsrController` 는 이식하지 않는다.** 001 실측 상속 클래스 **0건**으로
        `DefaultUsrController` 가 이미 대체했다(001 주석에도 "구 AbstractSiteUsrController").
        죽은 코드를 옮기면 다음 사람이 둘 중 어느 쪽이 진짜인지 다시 조사해야 한다

#### DoD

- 콘텐츠 작성 → 승인 4단계 → 게시 → 수정 시 이력 스냅샷 생성
  — 🟡 **코드 검증까지**. `ContentStatus.assertCanTransitionTo` 가 직행을 막고
    (`DRAFT→REVIEW→APPROVED→PUBLISHED`, 역행은 DRAFT 로만),
    `ContentServiceImpl.changeStatus:233` 이 이를 호출한다. 수정 경로 2곳
    (`:135`·`:179`)이 `snapshotToHistory` 를 부른다. **실제 왕복은 미실행**
- 게시판 8유형 중 대표 3유형 작성 → 상세 → 댓글 → 좋아요 → 신고
  — 🟡 **구성 확인까지**. 유형별 화면 9종(NOTICE·FREE·QNA·GALLERY·PDF·FILE·YOUTUBE·BODO·FAQ)
    + 왕복 컨트롤러 4종 존재. **실제 왕복은 미실행**
- 파일 방어 시나리오 차단 — 확장자 위조, 경로 조작, 허용 외 확장자
  — 🟡 **배선 확인까지**. 6중 방어가 `FileUploadServiceImpl` 에 전부 주입·호출된다:
    `FileExtensionValidator` · `TikaMimeDetector.detectAndValidate` · `FileStorage.saveToQuarantine`
    · `ImageReencoder` · `Sha256Hasher` · ClamAV(`virus_scan_status`).
    경로 containment 는 `FileStorage:162` 의 `resolved.startsWith(root)`.
    6번째 방어는 **외부 JAR 데몬**이 스캔하고 앱은 `FileServiceImpl:327·363` 에서
    `isDownloadable()` 로 INFECTED/ERROR 다운로드를 막는다. **공격 시나리오 미실행**
- 배너/팝업 없이도 레이아웃 정상 렌더(빈 컬렉션 안전값)
  — ✅ `SiteContextModelAdvice` 가 실패·미해석 시 `List.of()`/`Map.of()` 를 돌려준다
- ArchUnit 전건 통과 · 매퍼 XML 전량 `_maria.xml` · `${}` 0건
  — ✅ ArchUnit 10/10 · 전체 테스트 33건 · 비-maria XML 0 · `${}` 0 · `@Mapper` 0 ·
    인라인 `on*` 0 · 외부 script/link 0

> **🟡 3건은 앱을 띄워야 끝난다.** 이 세션에서는 8084 를 사용자 인스턴스가 점유 중이고
> DB·PII 키가 셸에 없어 기동하지 못했다. 마이그레이션 `V2026073104` 적용 후
> 사용자 환경에서 실행해야 한다 — 적용 전에는 `/bbs/**` 가 무매칭 DENY 로 302 다
> (2026-07-31 실측: 현재 기동 중인 인스턴스에서 `/bbs/x/y` → 302).

**범위 밖** — 회원 전용 권한(P5 이후), 통계(P7), 검색(외부 엔진으로 분리).

---

### P5 — 회원 · 인증 연동

**목표**: 회원 생명주기가 돈다.

#### 작업

- [x] `tb_member` + 가입(약관 동의 `tb_member_consent`·유형 선택)·로그인·마이페이지 (2026-08-01 이식)
- [x] 비밀번호 정책·변경·찾기, `tb_member_password_history` (2026-08-01 이식)
- [x] 통합 로그인 `v_user_login` VIEW — `R__v_user_login.sql` (2026-08-01).
      **001 의 EMPLOYEE UNION 지를 뺐다** — 로그인 주체는 MEMBER·STAFF 2종(D7).
      R__ 인 이유: 뷰는 정의 자체가 산출물이라 V__ 로 쌓으면 현재 정의를 알려면
      파일 전체를 시간순으로 읽어야 한다. R__ 는 이 파일 하나가 늘 현재 정의다
- [x] OAuth2 소셜 로그인 — 네이버·카카오·구글(`tb_member_oauth`), `config/oauth2/` (2026-08-01 이식)
- [x] **NICE 본인인증** — `primary/identity` (2026-08-01 이식).
      JPMS 플래그는 **P0 에서 이미 pom 에 있다**(`nice.jvm.args`) — surefire·spring-boot:run
      적용 확인. 운영 Tomcat setenv 는 배포 시 확인 항목으로 남는다
- [x] 휴면 전환·분리보관 — `tb_member_dormant`, `tb_member_dormant_notice` (2026-08-01 이식).
      `DormantBatchWorker`(REQUIRES_NEW 단건 격리)는 이식하고 **`DormantScheduler` 는 제외** —
      배치 자동 실행은 P7 이다. 워커는 스케줄러가 아니라 트랜잭션 경계 장치다
- [x] **휴면 해제 본인확인 — 실명인증 / 이메일 OTP 택1** (2026-08-01 **신규 개발**)
      - [x] 수단 선택 화면 — `front/dormant-restore.html`. 두 경로 모두 성공 시
            `DormantService.restoreVerified` 로 수렴(역이관 + 안내 메일)
      - [x] **A. 실명인증** — `NiceCheckUsrController` 세션 결과의 DI 를 `TokenHasher` 로
            해시해 `tb_member_dormant.di_hash` 와 대조. 매퍼 `findDormantByLoginIdAndDiHash` 신설.
            DI **원문을 조회 조건·파라미터로 흘리지 않는다**(로그·APM 에 개인식별값이 남는다)
      - [x] **B. 이메일 OTP** — `primary/member/otp` 신설.
            `tb_member_otp` 는 **베이스라인 DDL 에 이미 있었다** — 마이그레이션 불요(설계 완료분)
            - [x] 발송 — 6자리, TTL 5분, `TokenHasher` HMAC 저장, 쿨다운 60초·시간당 5회
            - [x] 수신처는 **휴면 스냅샷 이메일** — 입력 이메일 해시 일치 시에만 발송.
                  사용자 입력 주소로 보내지 않는다(대소문자·별칭 차이로 엉뚱한 곳에 갈 수 있다)
            - [x] 검증 — 상수 시간 비교, 시도 5회 초과 폐기, **소비는 `WHERE verified_at IS NULL`
                  UPDATE 의 반환 행 수로 판정**(자바 if 로는 동시 요청 둘이 함께 통과한다)
            - [x] 메일 템플릿 — `mail/account-dormant-otp.html` + `CODE_ACCOUNT_DORMANT_OTP`
            - [ ] 만료·사용 완료 OTP 정리 — 매퍼 `deleteExpiredBefore` 는 있고
                  **호출할 배치는 P7**(보존 배치 항목에 추가 필요)
      - [x] **계정 열거 차단** — `requestOtp` 는 **어떤 실패에도 예외를 던지지 않는다**
            (계정 없음·이메일 불일치·쿨다운·메일 실패 전부 정상 종료). 소요시간도 400ms
            하한으로 맞춘다 — 빠른 응답이 곧 "계정 없음" 신호가 되기 때문
      - [x] Bucket4j 레이트리밋 — `RateLimitFilter` 가 `/member/dormant/restore/**` POST 를
            **커버하지 않았다**(로그인 2종 + `/api/**` 만). IP 버킷을 적용했다.
            loginId 2차 키는 두지 않는다 — 계정별 버킷 자체가 관측 지점이 된다
      - [x] 정책값 `application.yml` 외부화 — `gopcms.member.otp.*` + 값의 근거 주석
- [x] 탈퇴 — `tb_member_withdraw` (2026-08-01 이식). `common/lifecycle` 3종이 P1 에서
      누락돼 있어 함께 이식했다(`WithdrawPurgeTarget`·`Properties`·`Executor`)
- [x] PII 암호화·마스킹 적용 + `log_privacy_access` 기록 지점 배치 (2026-08-01 이식).
      `primary/system/pii`(파기 로그) 6종도 함께 — `DormantBatchWorker` 가 참조한다
- [x] 관리자 — 회원 관리 화면, 마스킹 노출 (2026-08-01 이식)

> **이식 주의**: 001의 휴면 해제는 실명인증이 아니라 **로그인ID+이름+이메일+비밀번호 3요소 일치**
> (`DormantRestoreForm`)다. 003은 위 2수단 택1로 **대체**하므로 001 코드를 그대로 가져오지 않는다.
> `tb_member_otp` 는 001에 없는 신규 테이블이다.

#### DoD

- 가입 → 로그인 → 마이페이지 → 탈퇴 왕복
  — 🟡 **미실행**. 화면·컨트롤러·매퍼 전량 존재, member 체인(Order 20) 복원 완료
- DB에 PII 가 `{AG}` 암호문으로 저장 · 화면에서는 마스킹 노출
  — 🟡 **미실행**. `@Encrypt`·`MaskUtils` 는 P1 산출물이고 회원 DTO 가 이를 쓴다
- 소셜 로그인 3종 중 최소 1종 왕복
  — 🟡 **미실행**. 실제 왕복은 각 제공자 콘솔에 콜백 URL 등록이 선행돼야 한다
- 개인정보 조회 시 `log_privacy_access` 적재 확인
  — 🟡 **미실행**. `@PrivacyAccess` AOP 는 P1 에서 동작 확인됨
- **휴면 해제 — 두 수단 각각 왕복**
  — 🟡 **미실행**(둘 다). 실명인증은 NICE 실계정 연동이, OTP 는 SMTP 가 필요하다
- **OTP 부정 시나리오 차단 확인**
  - ✅ **만료 거부 · 사용 완료 재사용 거부 · 시도 5회 초과 폐기 · 재발송 쿨다운 ·
    평문 미보관** — `MemberOtpServiceTest` 13건으로 검증(DB 불요).
    저장값이 `hasher.hash(code)` 와 같고 평문과 다름을 직접 확인한다
  - 🟡 **미존재 계정 동일 응답** — 코드상 분기가 없어(예외를 던지지 않는다) 성립하지만,
    실제 응답 시간 분포는 기동 후 측정해야 확인된다
  - 🟡 **동시 요청 재사용 차단** — SQL `WHERE verified_at IS NULL` 이 보장한다.
    단위 테스트는 매퍼가 0을 돌려줄 때 거부하는 것까지만 본다

**범위 밖** — 휴면/탈퇴 **배치 자동 실행**(P7에서 스케줄러와 함께), 알림 발송(P6).

> **🟡 항목은 앱 기동 + 외부 연동이 있어야 끝난다.** 이 세션에서는 DB 자격증명·PII 키가
> 셸에 없어 기동하지 못했다. OTP 보안 요구만 단위 테스트로 확인했다 —
> 나머지는 사용자 환경에서 `R__v_user_login` · `V2026080101` 적용 후 실행해야 한다.

---

### P6 — 부가 도메인

**목표**: 나머지 도메인을 채운다.

#### 작업

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
- 설문: 발행 → 응답 → 중복 차단 → 집계
- 민원: 접수 → 채번 → 답변 → 상태 전이
- ArchUnit 전건 통과

**범위 밖** — 통계 대시보드(P7), 사이트 데모(P8),
**검색 전체**(외부 검색엔진을 `contextPath=/search` 로 분리 — 2026-07-31).

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
      `stat_content_view` + 대시보드
      (**검색어 통계 제외** — `stat_search_keyword` 삭제, 검색엔진 쪽에서 낸다)
- [ ] **로그 뷰어** — 관리자 조회 화면
- [ ] **ShedLock** — `config/shedlock/`, logging DB `shedlock` 테이블, `defaultLockAtMostFor`
- [ ] **스케줄러 8종** — 개발가이드 §12 표 전체. cron 외부화 + **dry-run 플래그**
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
4. **매퍼 XML 은 `*_maria.xml` 단일** — 다른 접미사 파일 0건 · `${}` 0건
5. 화면 작업 시 **인라인 핸들러 0건 · raw hex 0건**(개발가이드 §15 grep)
6. **커밋 전 `.env` 스테이징 여부 확인**
7. 페이즈 완료 = 체크박스 갱신 + **[RESULT.md](RESULT.md) 에 결과 추가** + 커밋
8. 적용된 Flyway 마이그레이션 파일은 **수정하지 않는다**(새 버전 추가)

---

## 6. 결정 대기

> 해당 페이즈 시작 전에 확정한다. 결정되면 이 표에 결과와 날짜를 적는다.

| # | 항목 | 선택지 | 필요 시점 | 결정 |
|---|---|---|---|---|
| D1 | **Flyway 실행 계정** — 앱 계정에 DDL 권한이 없는 기존 방침과 충돌 | ① DDL 권한 전용 flyway 계정 분리 ② 앱 계정에 DDL 부여 ③ 로컬·dev 만 활성, 운영은 DBA 집행 | **P0** | ✅ 2026-07-31 — **③ 로컬·dev 만 활성, 운영은 DBA 집행**. local/dev 는 `enabled=true` 기본, **prod 는 환경변수 override 없이 `false` 하드코딩**(정책을 권고가 아닌 강제로). 운영 스키마 변경은 DBA 가 같은 마이그레이션 파일을 Flyway CLI 로 집행한다 |
| D2 | **Flyway 적용 범위** | ① 3개 DB 전부 ② primary 만 | **P0** | ✅ 2026-07-31 — **① 3개 DB 전부**. DataSource별 Flyway 빈 3개 명시 구성 |
| D3 | **git 원격 저장소** — 사용 여부 및 URL | | **P0** | ✅ 2026-07-31 — `https://github.com/kingja51/pcms2026-003` (public). 기본 브랜치 `main` |
| D4 | **로컬 DB·데이터 경로** — 001과 공유할지 분리할지(스키마명, 업로드/로그 경로) | | **P0** | ✅ 2026-07-31 — 001과 **분리**. MariaDB 11.8.3 `pcms2026-003-{primary,logging,secondary}` (업로드·로그 경로 미정) |
| D12 | **역할 계층** — `tb_role_hierarchy` closure 구성 | ① 001 실측 계층 유지 ② 새로 정의 | **P2** | ✅ 2026-07-31 — **② 새로 정의**: `ROLE_SUPER > ROLE_ADMIN > ROLE_MANAGER > ROLE_STAFF > ROLE_MEMBER > ROLE_REAL`. `ROLL_MANAGER` → **`ROLE_MANAGER`** 오타 정정, **`ROLE_REAL`(실명인증)** 신설. `ROLE_SUPER` 는 지시에 없었으나 001 대로 최상위, `ROLL_PRIVACY` 는 계층 밖 독립(001 동일)·오타 표기 유지. **`ROLE_MEMBER > ROLE_REAL` 은 의도된 선택**(2026-07-31 재확인) — 상위가 하위 권한을 가지므로 모든 회원이 실명인증 역할을 자동 보유한다. 실명인증을 게이트로 쓰려면 계층에서 빼야 하나, 현재 설계에선 맞다 |
| D11 | **PII HMAC 키 분리** — 001 은 `*_hash` 산출에 AES `master-key` 를 재사용했다("별도 키 관리 간소화") | ① 001 대로 재사용 ② **HMAC 전용 키 분리** | **P1** | ✅ 2026-07-31 — **② 분리**. `gopcms.crypto.pii.hmac-key` / `PCMS_PII_HMAC_KEY` 신설. key separation 원칙 준수 + 감사 대응. 회전 비용이 master-key 보다 큰 점은 키 인벤토리에 명시 |
| D9 | **`tb_search_gemini_file`·`tb_search_gemini_keyword` 처리** — SDK 를 빼면서 이 2종을 쓰는 코드가 사라진다 | ① DDL 에서 삭제 ② 남겨 두고 미사용 | P6 | ✅ 2026-07-31 — **① 삭제**. 검색 테이블 7종 전량 삭제로 흡수(D10) |
| D10 | **검색 기능 배치** — 검색엔진을 어디에 둘지 | ① 앱 내장(`tb_search_*` 색인) ② **외부 검색엔진 `contextPath=/search`** | **P0** | ✅ 2026-07-31 — **② 외부 엔진**. `tb_search_*` 7종 삭제, P6 검색 항목·DoD 제거. 앱은 색인·검색어 수집을 하지 않는다 |
| D8 | **`tb_election_voter`·`tb_election_voter_import_job` 배치 DB** | ① primary ② **secondary(개별프로그램)** | **P0** | ✅ 2026-07-31 — **② secondary**. FK 없어 이동에 제약 변경 없음. `site_id` 는 primary `tb_site` 를 가리키지만 DB 분리로 조인 불가 → 사이트 필터는 `SiteContext` 로 |
| D7 | **`tb_employee` 의 로그인·권한 컬럼 처리** — 직원이 로그인 주체가 아니게 되어 `PASSWORD`·`two_factor_secret` 등이 死컬럼이 됐다 | ① 전부 DROP(권장 — 쓰지 않는 자격증명을 남기지 않는다) ② 남기되 애플리케이션에서 미사용 ③ 별도 이관 후 DROP | **P0** | ✅ 2026-07-31 — **① 전부 DROP**. **19컬럼** 제거(`employee_seq` 포함 — `v_user_login.uniq_id` 전용이라 혼동 요인), `uk_employee_login`·`uk_employee_seq`·`chk_employee_captcha_required_yn` 제거, `idx_employee_status` → `idx_employee_active(delete_yn, resign_date)` 대체, `AUTO_INCREMENT` 옵션 제거 |
| D5 | **Namo CrossEditor 4** 납품 패키지 확보 시점 및 제품 API 확인 | | P3 | |
| D6 | 사이트 데모 시각 언어(KRDS / IBM Carbon / 기타) | | P8 | |

---

## 7. 발견 사항 (범위 밖 이슈 기록)

> 작업 중 발견했으나 현재 페이즈 범위가 아닌 것을 **고치지 말고 여기 기록**한다.
> 처리 여부는 사용자가 정하고, 처리하기로 하면 해당 페이즈에 항목으로 추가한다.

| 발견일 | 내용 | 발견 페이즈 | 심각도 | 처리 |
|---|---|---|---|---|
| 2026-07-31 | **001 MyBatis 설정이 `_maria.xml` 을 하드코딩**한다(`{Primary,Secondary,Logging}MyBatisConfig` 의 `classpath*:mapper/**/*_maria.xml`). 드라이버 환경변수와 무관하게 maria 만 로드된다 — mysql·postgres XML 84개씩이 실제로는 사용되지 않는 상태 | P0 | 중 | ✅ **해결 2026-07-31** — 003 은 **maria 단일 확정**. 접미사 `_maria` 는 유지. CLAUDE/AGENTS/README/개발가이드 §2·§3·§6-2·§6-4·§6-5·§15, PLAN 상시게이트 4·P4·§8 반영 |
| 2026-07-31 | **`sql/` 덤프에 뷰 5개 미반입** — 라이브 `pcms2026_primary` 에 뷰 9개인데 덤프는 4개 | P0 | 중 | **부분 해결 2026-07-31** — 검색 뷰 **4종**(`v_bbs_article_search`·`v_file_search`·`v_menu_search`·`v_schedule_search`) **미반입 확정**(D10 검색 외부 분리). 003 DDL·DB 양쪽에 부재 확인. **`v_content_published` 는 별건 — 아래 행 참조** |
| 2026-07-31 | **`v_content_published` 미반입** — 001 실측 정의는 검색과 무관한 **콘텐츠 게시 필터 뷰**다(`delete_yn='N'` + `STATUS='PUBLISHED'` + `published_at<=now` + `unpublish_at>now`). 사용자 화면에서 게시중 콘텐츠만 노출하는 용도라 **P4 콘텐츠 도메인에 필요할 가능성이 높다** | P4 | 중 | 미정 — P4 착수 시 이식 여부 결정. 이식하면 반복 마이그레이션 `R__` 로 관리 |
| 2026-07-31 | **secondary 덤프 7테이블 누락** — 라이브 10 vs 덤프 3. `tb_g2b_*` 4종 + `tb_lab`·`tb_staff`·`tb_syllabus`. 003 이식 대상인지 미결 | P0 | 중 | **부분 해결 2026-07-31** — `tb_g2b_*` 4종은 **미이식 확정**(`g2b` yml 블록·`PCMS_G2B_SERVICE_KEY`·키 인벤토리 항목 제거). `tb_lab`·`tb_staff`·`tb_syllabus` 3종은 **여전히 미정** |
| 2026-07-31 | **`sql/` 구조가 개발가이드 §3 과 불일치** — 가이드는 `sql/{mariadb,mysql,postgres}/`, 실제는 플랫 3파일 MariaDB 전용 | P0 | 중 | 미정 |
| 2026-07-31 | **primary DDL 실행 불가 2건** — ① `tb_member_otp`·`tb_template` 종결 세미콜론 누락 ② FK forward reference 로 `SET FOREIGN_KEY_CHECKS=0` 헤더 필요. 원본 그대로면 첫 테이블에서 errno 150 | P0 | **높음** | ✅ **해결 2026-07-31** — 세미콜론 2건 추가, 파일 앞뒤에 `SET FOREIGN_KEY_CHECKS = 0/1` 추가(사유 주석 포함). forward reference 실측 **25건**(기록의 26건은 부정확). secondary·logging 은 forward reference 0건이라 헤더 불필요 |
| 2026-07-31 | **`DROP TABLE IF EXISTS` 누락 5건** — `tb_member_otp`·`tb_site`·`tb_template`·`tb_theme`·`tb_layout`. 나머지는 있어 재실행이 안 된다 | P0 | 중 | ✅ **해결 2026-07-31** — 5건 추가. primary 62개 테이블 전부 `DROP TABLE IF EXISTS` 보유 → 재실행 가능 |
| 2026-07-31 | **뷰 4종에 `DROP VIEW IF EXISTS` 가 없어 재실행 실패** — 테이블만 고치고 실측했더니 2회차에서 `ERROR 1050: Table 'v_site_menu' already exists`. 정적 검사로는 안 잡히고 실행해야 드러난다 | P0 | 중 | ✅ **해결 2026-07-31** — `CREATE VIEW` → **`CREATE OR REPLACE VIEW`** 4건. DROP 추가보다 간결하고 멱등 |
| 2026-07-31 | **logging 세미콜론 누락은 오탐** — 검사 스크립트가 `) ENGINE=` 줄만 보고 `log_access`·`log_error` 를 오탐했다. 실제로는 `PARTITION BY RANGE` 절이 뒤따르고 `);` 로 정상 종결된다. 파티션 테이블을 검사할 때 주의 | P0 | 낮음 | ✅ 확인만 — 수정 불요 |
| 2026-07-31 | **참조 테이블 20개의 `site_code` 가 비인덱스·비FK·nullable 중복** — 모든 유니크·인덱스는 `site_id` 선두. `v_site_menu` 조차 `tb_site` 를 조인해 `s.site_code` 를 쓴다. 제거 또는 복합 FK+CASCADE 중 택1 | P4 | 중 | 결정 대기 |
| 2026-07-31 | **`tb_site` 를 참조하는 FK 부재** — 20개 테이블이 `site_id` 를 갖지만 `REFERENCES tb_site` 가 하나도 없다(자기참조 `fk_site_parent` 만 존재). 의도인지 누락인지 확인 필요 | P4 | 중 | 미정 |
| 2026-07-31 | **`tb_layout`·`tb_theme` 가 문서에 없음** — 개발가이드 §5-1 인벤토리와 PLAN P4 미반영. P4 는 아직 "`tb_site.theme` 연동" 으로 적혀 있으나 실제는 `tb_template`→`tb_layout`/`tb_theme`→`tb_site` 3단 구조 | P4 | 중 | 미정 |
| 2026-07-31 | **와이어프레임 링크 14개 깨짐** — `wireframe/index.html` 이 frame001~007·011~017 을 링크하나 디렉터리는 frame021~028 만 존재. `tb_layout.wireframe_ref` 값 채울 때 영향 | P8 | 낮음 | 미정 |
| 2026-07-31 | **와이어프레임 raw hex 426건 + Google Fonts CDN `@import`** — 현 CSP·self-host 폰트 규약과 충돌. 데모 이식 전 KRDS 토큰·self-host 로 변환 필요(인라인 핸들러는 0건) | P8 | 낮음 | 미정 |
| 2026-07-31 | **PLAN P0 진입점 클래스명 불일치** — PLAN 은 `GopcmsApplication`, 001 실측은 `Pcms2026Application`. DataSource 계열(`GopcmsDataSourceProperties`)은 일치 | P0 | 낮음 | ✅ **해결 2026-07-31** — **`Pcms2026Application` 채택**(사용자 결정). PLAN P0 항목 갱신. DataSource 계열은 `Gopcms*` 유지 |
| 2026-07-31 | **[eGov 호환성] `@Mapper` 는 5.0 기준 위반** — 호환성 가이드 규칙 5-①-2)는 Mapper Interface 사용 시 표준프레임워크 `MapperConfigurer` + **`@EgovMapper`** 를 요구하고 `@Mapper` 는 **실행환경 v4.3 이하** 표기로 명시. 현재 개발가이드 R4(:173)·§6-2(:279)·§6-4(:385) 와 CLAUDE.md 가 전부 `@Mapper` | P0 | **높음** | ▶ P0 작업·DoD 및 P1 R4 룰에 반영. **개발가이드·CLAUDE.md 본문 수정은 별도 커밋** |
| 2026-07-31 | **[eGov 호환성] 실행환경 필수 4종 의존성이 문서에 없음** — 규칙 1-② 는 `egovframe-rte-ptl-mvc`·`-fdl-cmmn`·`-psl-dataaccess`·`-fdl-logging` **동일 버전** 적용을 요구하나, 개발가이드에 eGov 의존성 항목 자체가 없다(`EgovAbstractServiceImpl` 언급만 존재) | P0 | **높음** | ▶ P0 작업·DoD 에 반영 |
| 2026-07-31 | **[eGov 호환성] Spring Boot 버전 상한** — 5.0 실행환경 기준선은 **3.5.6**(Spring 6.2.11 / Security 6.5.5 / MyBatis 3.5.19). 현 지정 **3.5.9** 는 규칙 1-② 예외("패치 버전 한해 최신 허용")로 **적법**하나, minor 상향 시 즉시 위반 | P0 | 중 | ▶ P0 작업에 상한 고정 항목 추가 |
| 2026-07-31 | **[eGov 호환성] R3 Service 예외가 규칙 4("예외 없음")와 충돌** — 개발가이드 R3 의 "모니터링·AI 캐시 등 eGov 계층 무관 기술 서비스" 예외는 회색지대. 규칙 4 권장안은 `EgovAbstractServiceImpl` 상속 **공통 추상 서비스** 경유 | P1 | 중 | ▶ P1 작업에 반영 |
| 2026-07-31 | **[eGov 호환성] 001 의 `Egov` 접두 클래스 이식 시 위반** — 규칙 6-② 상 실행환경 클래스를 상속한 클래스는 `Egov` 로 시작할 수 없다. 그대로 복사하면 위반 | 전 페이즈 | 중 | ▶ §2 진행 규칙 7 로 승격 |
| 2026-07-31 | **`DynamicAuthorizationManager` 위치가 001 실측과 003 문서에서 다르다** — 001 은 `primary/system/access/service`, 003 개발가이드 §4-1·PLAN P2 는 `config/access/`. 문서 2곳이 일치하므로 **문서를 따랐다**. 패키지 이동으로 같은 패키지였던 `RoleUrlAccessService` 임포트 1줄이 추가됐다 | P2 | 낮음 | ✅ 처리 — `config/access/` 채택. 되돌리려면 §7 에 기록 후 결정 |
| 2026-07-31 | **역할 코드에 `ROLE_` 오타** — 001 실측 `ROLL_EDITOR`·`ROLL_MANAGER`·`ROLL_PRIVACY`. P2 시드에 실측 그대로 넣었다(원칙 6). `tb_admin.role_codes` CSV 와 인가 매칭에 쓰이는 데이터 계약이라 임의로 바꾸지 않는다 | P2 | 중 | **부분 처리 2026-07-31** — `ROLL_EDITOR` 는 미사용 확정으로 **삭제**(`V2026073102__delete_roll_editor_role.sql`). 참조 4곳 0건 확인 후 물리 삭제. `ROLL_MANAGER`·`ROLL_PRIVACY` 는 **권한만 만들어 둔 상태로 유지** — 오타 표기도 실측 그대로 |
| 2026-07-31 | **인가 거부 시 `/member/login` 으로 리다이렉트하는데 그 화면이 없다** — default 체인의 로그인 페이지 설정(001 설계). member 체인·회원 화면이 P5 라 그때까지 404 가 된다. 관리자는 `/admin/login` 으로 정상 이동 | P5 | 낮음 | 미정 |
| 2026-07-31 | **fail-fast 가 설계된 동작이 아니라 우연한 부수효과다** — 실측: `PCMS_PII_MASTER_KEY` 를 미주입해도 앱이 정상 기동했다. yml 에 `${VAR}`(기본값 없음)를 써도, 그 프로퍼티를 바인딩하는 `@ConfigurationProperties` 빈이 없으면 Spring 은 placeholder 해석 자체를 시도하지 않는다. 바인딩되는 `gopcms.datasource.*` 조차 부팅은 실패하되 메시지가 `Driver claims to not accept jdbcUrl, ${PCMS_DB_PRIMARY_URL}` 로 **원인이 드러나지 않는다**(placeholder 문자열이 Hikari 까지 그대로 전달) | P0 | **높음** | 미정 — 후보: ① Properties 클래스에 `@Validated`+`@NotBlank` ② 기동 시 필수 환경변수 목록을 검사하는 `EnvironmentPostProcessor`. **P1 에서 방식을 확정**하고 그때 전 Properties 에 일괄 적용 |
| 2026-07-31 | **JODConverter 가 기동 때마다 ERROR 를 남긴다** — `Could not delete '...\.jodconverter_socket_...\extensions.pmap'`. 앱이 죽어도 `soffice.bin` 이 남아 프로필 디렉터리를 잡는다. 기동·health 무영향이지만 **진짜 오류(포트 충돌)를 가려 원인 추적을 3회 방해**했다 | P4 | 중 | ✅ **처리 2026-07-31** — `application-local.yml` 에서 `gopcms.file.converter.enabled=false`. 운영은 true 유지. 문서 뷰어 착수(P4) 시 재활성하고, 잠금 재발하면 soffice.bin 종료 + 프로필 디렉터리 삭제 |
| 2026-07-31 | **Flyway 11.7.2 가 MariaDB 11.8 을 공식 지원하지 않는다** — 기동 시 `Flyway upgrade recommended: MariaDB 11.8 is newer than this version of Flyway... latest supported version is 11.2` WARN. 베이스라인 생성·검증은 정상 동작했다 | P0 | 낮음 | 미정 — 실제 마이그레이션 적용 시 재확인. 문제되면 Flyway 버전 상향(Spring Boot BOM override) |
| 2026-07-31 | **001 yml 에 비밀값 평문이 다수 커밋돼 있다** — `application-dev.yml` 의 DB 비밀번호 3개(평문 기본값), `application.yml` 의 지도 API 키 3종 실제값(Kakao appkey·Naver client-id·Google Maps key), Gmail 발신 주소, 개발자 개인 ngrok 도메인 4곳. **값 자체는 이 문서에 적지 않는다** — 001 저장소를 직접 확인할 것. **003 은 전량 기본값 없는 `${VAR}` 로 전환**했다 | P0 | **높음** | ✅ **처리 2026-07-31** — 이식 시 제거. 001 저장소 자체의 키 회수·폐기 여부는 별건 |
| 2026-07-31 | **001 pom 은 `egovframe-rte-fdl-logging` 을 전 모듈에서 exclusion** — log4j2↔SLF4J 양방향 브리지 충돌(`log4j-slf4j2-impl cannot be present with log4j-to-slf4j`) 회피가 목적이었으나, **호환성 규칙 2-① 필수 4종 위반**이다. 003 은 모듈을 살리고 `log4j-core`/`log4j-slf4j2-impl` 만 제외 | P0 | **높음** | ✅ **해결 2026-07-31** — pom 이식 시 반영 후 `dependency:tree` 로 실측 확인. `log4j-to-slf4j:2.24.3` + `log4j-api:2.24.3` 만 존재하고 `log4j-slf4j2-impl`·`log4j-core` 는 0건. (`log4j-over-slf4j` 는 log4j **1.x** 브리지라 세대가 달라 충돌 대상 아님) |
| 2026-07-31 | **`egovframe-rte-psl-dataaccess` 의 JPA transitive** — JPA 금지 프로젝트인데 무엇이 딸려오는지 미확인이었다 | P0 | 낮음 | ✅ **해결 2026-07-31** — 실측 결과 `hibernate-core`(ORM 본체)는 **유입되지 않는다**. `jakarta.persistence-api:3.1.0` + `spring-orm:6.2.15` 만 들어오며 둘 다 API·추상화라 **exclusion 불필요**(제외하면 rte 클래스 로딩 시 NoClassDefFoundError 위험). JPA 미사용은 ArchUnit 규약으로 강제한다. `hibernate-validator` 는 Bean Validation 구현체로 JPA 무관 |
| 2026-07-31 | **`lucene-analysis-nori` 이식 제외** — 001 실측상 `primary/search/` 전용(`KoreanTokenizer`·`SearchIndexServiceImpl`). 검색이 외부 엔진으로 나가 소비자가 없다 | P0 | 낮음 | ✅ **처리 2026-07-31** — pom 에서 제외 |
| 2026-07-31 | **Google GenAI SDK 제외에 딸린 잔재** — `tb_search_gemini_*` 2종, `tb_file_group.entity_type` 주석의 `GEMINI_SEARCH` 예시 | P0 | 중 | ✅ **해결 2026-07-31** — 검색 테이블 7종 전량 삭제(D10) 로 흡수. `GEMINI_SEARCH` 주석도 제거 |
| 2026-07-31 | **검색 삭제 후 `stat_search_keyword`(logging) 가 고아로 남는다** — 앱이 검색어를 수집하지 않으므로 채울 주체가 없다 | P7 | 중 | ✅ **해결 2026-07-31** — 테이블 삭제. P7 통계 항목·개발가이드 §5-1 statistics 행에서도 제거. 검색어 통계는 검색엔진 쪽 책임 |
| 2026-07-31 | **`tb_employee` 에 로그인·권한 컬럼이 死컬럼으로 남는다** — 직원을 로그인·권한에서 제외했으나 `login_id`·`PASSWORD`·`STATUS`·`login_fail_count`·`locked_until`·`password_changed_at`·`password_expire_at`·`two_factor_enabled_yn`·`two_factor_secret`·`ip_whitelist`·`allowed_time_from/to`·`role_ids`·`group_ids`·`captcha_required_yn`·`last_login_at/ip` 가 그대로다. 특히 **`PASSWORD`·`two_factor_secret` 은 쓰지 않는 자격증명 저장소**라 보안 부채 | P0 | **높음** | ✅ **해결 2026-07-31** — D7 ① 채택, 18컬럼 DROP |
| 2026-07-31 | **`user_type` CHECK 제약에 `'EMPLOYEE'` 가 14곳 잔존** — `tb_role_url_access` 등 enum 과 `role_codes` CHECK(`ROLE_EMPLOYEE`). 로그인 주체에서 빠졌으므로 값이 들어올 일이 없다. 다만 기존 로그 행 호환을 위해 남길 수도 있어 판단 필요 | P0 | 중 | ✅ **해결 2026-07-31** — 전량 제거. CHECK 제약 8곳(`tb_bbs_article`·`tb_bbs_comment`·`tb_bbs_like`·`tb_bbs_report`·`tb_complaint_answer`·`tb_pii_purge_log`·`chk_bbs_master_download_auth`·`chk_file_group_download_auth`) + 컬럼 주석 11곳(primary 6·logging 5). `ROLE_EMPLOYEE` 도 함께 제거 |
| 2026-07-31 | **이름 컬럼 암호화 정책이 테이블마다 다르다(실측)** — `tb_member.member_name`·`tb_member_dormant.member_name` 은 **평문** `varchar(150)`, `tb_employee.employee_name` 만 `{AG}` 암호문 `varchar(512)`. 개발가이드 §10-4 에 실측대로 표로 정리함 | P0 | 낮음 | ✅ **문서 반영 완료** — 코드 정책(`@Encrypt` 부착 대상)은 P1 에서 확정 |
| 2026-07-31 | **maria 단일 확정이 개발가이드 2곳에 미반영** — §13 명명 규약의 Mapper 행이 `XxxMapper_{maria,mysql,postgres}.xml`, §7-2 XML 예시 주석이 `(mysql/postgres 동시 작성)`. 2026-07-31 maria 단일 확정 커밋에서 누락된 잔재 | P0 | 낮음 | 미정 — 호환성 반영과 별개 주제라 손대지 않음 |
| 2026-07-31 | **[eGov 호환성] Flyway 는 충돌 없음(확인 완료)** — 규칙 1(rte 바이너리)·2(Spring 버전)·3(트랜잭션/커넥션풀)·5·6 어디에도 저촉 안 됨. 규칙에 마이그레이션 도구 지정 조항 자체가 없다. **단 Flyway 를 `@Service`/`@Repository` 로 감싸면** 규칙 4·5 의 "실질적으로 데이터를 처리하는 클래스" 로 잡혀 오탐 위반 가능 → `@Configuration` 안의 `Flyway` 빈 정의로만 유지(개발가이드 §6-5 현행 방식이 그대로 정답) | P0 | 낮음 | ✅ **확인 완료 2026-07-31** — 조치 불요, 주의사항만 유지 |
| 2026-07-31 | **템플릿 전량 이식 후 인라인 `on*=` 핸들러 28건 검출** — `onsubmit="return confirm(…)"` 23, `onchange="this.form.submit()"` 2, `onclick="previewMail()"` 1, `onerror="this.style.visibility='hidden'"` 2. CSP `script-src` 에 `'unsafe-inline'` 이 없어 **브라우저가 조용히 무시**한다 — 001 의 "확인창 없이 삭제" 장애와 동일 계열 | P4 | **높음** | ✅ **해결 2026-07-31** — 전량 `data-*` 위임 계약으로 치환. `data-confirm` 23, `data-action="submit-on-change"` 2, `data-action="preview-mail"` 1, `data-hide-on-error` 2. `submit-on-change` 계약은 `app.js` 에 신규 구현(§9-2 표에는 있었으나 미구현이었다), `preview-mail` 은 해당 화면 nonce 스크립트에 위임 등록. 재검사 **0건** |
| 2026-07-31 | **CDN `<script src>` 잔존** — `admin/system/sample/tui-editor.html` 이 TOAST UI Editor 를 `uicdn.toast.com` 에서 로드. `strict-dynamic` 하에서 host 화이트리스트는 무시되므로 **차단**된다. 매핑 컨트롤러·링크 참조 0건인 고아 파일이고, 003 의 에디터는 tiptap 으로 확정돼 있다 | P4 | 중 | ✅ **해결 2026-07-31** — 파일 삭제(`templates/admin/system/sample/`). 외부 `<script>/<link>` 재검사 **0건**. 남은 외부 참조 4건은 Google Maps `<iframe>`·`<a>` 로 `script-src` 무관이며 `frame-src` 에 `https://www.google.com` 이 이미 있어 **차단되지 않는다**(CspNonceFilter:161) |
| 2026-07-31 | **이식 템플릿의 색상 규약 위반 대량** — Tailwind 기본 팔레트 **1,049건**(`bg-blue-500` 류), raw hex **299건**(메일 템플릿 제외 시 **143건**). KRDS 시맨틱 토큰 원칙 위반이나 **기능 결함은 아니다**(인라인 핸들러·CDN 과 달리 조용한 오작동이 없다) | P4 | 중 | **P8 이월**(사용자 결정 2026-07-31) — "인라인 핸들러·CDN 만 우선 교정, 색상은 P8 데모 정비 때". 메일 템플릿 raw hex 는 **정상**(이메일 클라이언트가 CSS 변수 미지원 — CLAUDE.md 예외 명시) |
| 2026-07-31 | **개인 Gmail 주소가 고객센터·발신 기본값으로 하드코딩** — `MailTemplateMngController:200` 의 self-test 수신자 fallback, 같은 파일 `:237` 의 `supportUrl`, 메일 템플릿 4종(`member-welcome`·`member-withdraw`·`account-dormant`·`password-changed`)의 "고객센터" `mailto:`. 001 잔재다. 저장소가 public 이지만 **git author 이메일과 동일해 새로 유출되는 정보는 없다**. 문제는 기능 쪽 — 템플릿 발신자 미설정 상태에서 "테스트 발송" 을 누르면 **운영에서 개인 주소로 메일이 나간다** | P4 | 중 | 미정 — 범위 밖이라 기록만. 처리 시 `gopcms.mail.support-address` 로 외부화하고 미설정이면 발송을 거부(fallback 금지)하는 편이 안전 |
| 2026-07-31 | **기존 목록 화면 65개가 여전히 인라인 페이징이다** — `fragments/pagination` 을 만들었지만 채택한 화면이 아직 0곳이다. 65개 중 상당수(`front/g2b/*`·`front/lfios/*`·`front/survey/*`·`front/complaint/*`·`front/schedule/*`)는 **자바 도메인이 미이식**이라 지금 손대도 검증할 수 없고, 화면마다 baseUrl 과 보존 파라미터가 다르다 | P4 | 중 | **후속 작업으로 분리**(2026-07-31) — 조각 자체는 테스트 13건으로 검증됐다. 전환은 ① P4 소관 화면(board·content·file·site·menu·code) 먼저 ② 나머지는 해당 도메인 이식 페이즈에서. **앱 기동 검증 없이 65개를 일괄 치환하지 않는다** — 001·002 의 범위 확대 실패 패턴이다 |
| 2026-07-31 | **사이트별 접근 규칙 `/{sc}`·`/{sc}/**` 를 넣을 수 없다** — `DefaultUsrController` 는 `/{sc}` 를 패턴으로 받지만 `tb_role_url_access` 는 **사이트마다 2행**이 필요하다(001 실측 48개 × 2 = 96행). catch-all `/*` 를 넣으면 미등록 site_code 까지 열린다. `tb_site` 행이 아직 0이라 지금 넣을 대상도 없다 | P4 | 중 | 미정 — 사이트 등록 화면이 규칙까지 함께 생성하는 것이 최종 형태(P8). 그전까지는 사이트를 만들 때 손으로 2행씩 넣어야 하고, **빠뜨리면 그 사이트만 조용히 404/302 가 된다** |
| 2026-07-31 | **일정(schedule) 템플릿 12종이 컨트롤러 없이 떠 있다** — 템플릿 전량 이식으로 `admin/system/schedule*`·`front/schedule/*` 가 들어왔으나 자바 도메인은 P6 다. 사이트 홈 템플릿 48종도 `scheduleMasters`·`upcomingSchedules` 를 참조한다 | P4 | 낮음 | 확인만 — 참조 전부 `th:if="${... != null and !#lists.isEmpty(...)}"` 로 감싸져 있어 **미주입 시 해당 섹션만 비고 예외는 없다**(실측). P6 에서 `DefaultUsrController.injectLandingData` 에 주입을 되살리면 템플릿 수정 없이 살아난다 |
| 2026-07-31 | **`/prg` 쉘의 목록 조각이 404 다** — `ProgramUsrController`(primary)는 이식했으나 실제 목록을 반환하는 001 의 `ProgramDataUsrController`(secondary, `/prg/{program}/{siteCode}/list`)는 `tb_lab`·`tb_staff`·`tb_syllabus` 이식 여부가 미결이라 제외했다. 쉘·레이아웃까지만 렌더된다 | P4 | 낮음 | 미정 — secondary 3테이블 결정(§7 상단 행)과 함께 처리한다. 데이터 계층이 들어오면 쉘은 수정 없이 동작한다 |
| 2026-08-01 | **`common/lifecycle` 3종이 P1 에서 누락됐다** — `WithdrawPurgeTarget`·`WithdrawPurgeProperties`·`WithdrawPurgeExecutor`. `application-local.yml` 의 `gopcms.lifecycle.withdraw-purge.dry-run` 이 이미 이걸 전제하고 있었는데 클래스가 없었다. P5 의 `MemberWithdrawPurgeTarget` 이 인터페이스를 구현하면서 드러났다 | P5 | 중 | ✅ **해결 2026-08-01** — 3종 이식. 참고로 `RetentionProperties`·`WithdrawPurgeProperties` 는 아직 **빈으로 등록되지 않았다**(소비자인 스케줄러가 P7). 실행기는 `AuditLogger` 만 주입받아 현재 기동에 문제 없음 |
| 2026-08-01 | **레이트리밋이 휴면 해제 경로를 커버하지 않았다** — `RateLimitFilter` 는 `/admin/login`·`/member/login`·`/api/**` 만 본다. 휴면 해제는 **로그인 전 누구나** 접근하면서 계정 존재 여부를 다루고 메일 발송을 유발한다. 제한이 없으면 ① 시도를 무한 반복해 응답 시간 차이로 계정을 열거할 수 있고 ② OTP 쿨다운은 계정당이라 여러 아이디로 돌리면 메일 폭탄이 우회된다 | P5 | **높음** | ✅ **해결 2026-08-01** — `POST /member/dormant/restore/**` 에 IP 버킷 적용(API 버킷 공용). loginId 2차 키는 **의도적으로 두지 않았다** — 계정별 버킷 자체가 관측 지점이 된다 |
| 2026-08-01 | **member 체인의 permitAll 패턴이 실제 URL 과 어긋난다** — 체인은 `/member/find/**` 인데 컨트롤러는 `/member/find-id`·`/member/find-password`(하이픈)를 매핑한다. 즉 아이디·비밀번호 찾기가 체인 permitAll 에 걸리지 않는다. 001 에서 그대로 넘어온 불일치 | P5 | 중 | **부분 처리 2026-08-01** — `V2026080101` 에 두 경로 `PERMIT_ALL` 규칙을 넣어 동작은 보장했다. **체인 패턴 자체를 고치는 것이 근본적**이지만 P2 산출물 수정이라 별건으로 둔다 |
| 2026-08-01 | **001 의 휴면 해제 3요소 방식을 폐기했다** — `DormantRestoreForm`·`DormantRestoreUsrController`(구) 미이식, `DormantService.restoreWithCredentials` 삭제. 휴면 계정의 비밀번호는 사용자가 가장 잊기 쉬운 정보라, 그걸 요구하면 **정작 본인이 못 푸는 화면**이 된다 | P5 | 낮음 | ✅ **처리 2026-08-01** — 실명인증 / 이메일 OTP 택1 로 대체(개발가이드 §10-6). 확인과 역이관을 분리해 `restoreVerified(memberId)` 로 수렴시켰다 |
| 2026-08-01 | **OTP 정리 배치가 없다** — `MemberOtpMapper.deleteExpiredBefore` 는 만들었지만 호출할 스케줄러가 없다. 만료·사용완료 행이 무한 누적된다 | P7 | 중 | 미정 — P7 보존 배치(`gopcms.retention`)에 `tb_member_otp` 버킷을 추가하는 것이 자연스럽다. 지금은 행이 작아 급하지 않다 |
| 2026-07-31 | **`pageQuery` 가 CSRF 토큰을 페이지 링크에 흘린다** — 신설한 `SiteContextModelAdvice.injectPageQuery` 가 `page` 만 빼고 전 파라미터를 복사했다. Spring Security 는 토큰을 읽은 뒤에도 파라미터 맵에서 지우지 않으므로, POST 가 뷰를 직접 렌더하는 경로(검증 실패 시 폼 재렌더 — `BoardCategoryMngController:79` 등 실재)에서 **모든 페이지 링크 href 에 토큰이 박힌다**. Referer 로 외부 유출 + 브라우저 히스토리 + `log_access` 적재 | P4 | **높음** | ✅ **해결 2026-07-31** — 코드 리뷰에서 검출. 요청의 `CsrfToken` 에서 실제 파라미터명을 읽어 제외하고, 못 읽으면 기본값 `_csrf` 로 막는다(커스텀 이름 대응). 테스트 2건 추가. **조각 채택 화면이 0곳이라 실제 유출은 없었다** — 65개 목록 전환 전에 잡았다 |
| 2026-07-31 | **`/fileDown/{id}/thumb` 이 접근통제 없이 열렸다** — `FileServiceImpl.downloadThumbnail` 만 `enforceDownloadAuth` 가 없었다(`download`·`previewInline`·`downloadGroup` 은 전부 있음). 무매칭 DENY 시절엔 도달 불가였으나 P4 의 `/fileDown/**` PERMIT_ALL 규칙이 이 경로를 열어 **MEMBER 전용 첨부의 썸네일이 UUID 만 알면 익명에게 노출**되는 상태가 됐다 | P4 | **높음** | ✅ **해결 2026-07-31** — 코드 리뷰에서 검출. `enforceDownloadAuth` 추가. **바이러스 게이트(`isDownloadable`)는 넣지 않았다** — 썸네일은 `ThumbnailGenerator` 가 새로 만든 JPG 라 원본 악성코드를 옮기지 않는다는 인터페이스 javadoc 의 **의도된 예외**가 맞다. 그 논리는 악성코드 전파만 다루고 접근통제는 다루지 않는다는 점이 누락 지점이었다 |

---

## 8. 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| **범위 확대**(001·002 재발) | 일정 지연·되돌리기 | §2 진행 규칙, 페이즈별 "범위 밖" 명시, §7 발견 사항 기록 |
| **인라인 핸들러가 CSP에 조용히 차단** | 확인창 없이 삭제 실행 등 무증상 결함 | P3에서 위임 규약 확정, DoD에 grep 검사 |
| **다른 벤더 DB 로 전환 요구** | maria 단일이라 매퍼·DDL 전량 재작성 | 단일 벤더는 의도된 선택(§6-4). 전환 시 별도 페이즈로 다룬다 |
| **신규 URL 접근 규칙 누락** | 화면이 열리지 않음 | 상시 게이트 3 |
| **정적 자원 permitAll 누락** | 폰트·CSS 가 조용히 폴백 | P2 작업 항목에 명시, P3 DoD에서 200 확인 |
| **비밀값 커밋** | 자격증명 유출 | `.env.example` 템플릿 원칙, 상시 게이트 6 |
| **Flyway DDL 권한 정책 충돌** | 운영 적용 불가 | D1 조기 확정 |
| **이식과 리팩터링 혼재** | 문제 원인 추적 불가 | §2-6 — 이식은 실측 그대로, 개선은 별도 항목 |
| 테스트가 얇음 | 회귀 탐지 지연 | ArchUnit + grep 게이트를 자동 검증의 축으로 |
