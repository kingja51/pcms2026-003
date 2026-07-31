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
| **P3** | 프런트 공통 기반 — KRDS·JS 규약·에디터 | 레이아웃·에디터 | ⬜ |
| **P4** | 핵심 CMS — 사이트·메뉴·콘텐츠·게시판·파일 | CMS 본체 | ⬜ |
| **P5** | 회원 · 인증 연동 | 회원 생명주기 | ⬜ |
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
- [ ] 매퍼 XML 작성 — **`*_maria.xml` 단일**(MariaDB 전용, 개발가이드 §6-4)

#### DoD

- 콘텐츠 작성 → 승인 4단계 → 게시 → 수정 시 이력 스냅샷 생성
- 게시판 8유형 중 대표 3유형 작성 → 상세 → 댓글 → 좋아요 → 신고
- 파일 방어 시나리오 차단 — 확장자 위조, 경로 조작, 허용 외 확장자
- 배너/팝업 없이도 레이아웃 정상 렌더(빈 컬렉션 안전값)
- ArchUnit 전건 통과 · 매퍼 XML 전량 `_maria.xml` · `${}` 0건

**범위 밖** — 회원 전용 권한(P5 이후), 통계(P7), 검색(외부 엔진으로 분리).

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
      - [ ] **B. 이메일 OTP** — **`tb_member_otp` 신규**(마이그레이션 + MariaDB DDL)
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
| 2026-07-31 | **역할 코드에 `ROLE_` 오타 3건** — 001 실측 `ROLL_EDITOR`·`ROLL_MANAGER`·`ROLL_PRIVACY`. P2 시드 마이그레이션에 **실측 그대로** 넣었다(원칙 6). 데이터가 쌓이기 전인 지금이 고치는 비용이 가장 싸지만, `tb_admin.role_codes` CSV 와 인가 매칭에 쓰이는 데이터 계약이라 임의로 바꾸지 않았다. **적용된 마이그레이션은 수정 불가**이므로 바꾸려면 새 버전 파일이 필요하다 | P2 | 중 | **결정 대기** |
| 2026-07-31 | **인가 거부 시 `/member/login` 으로 리다이렉트하는데 그 화면이 없다** — default 체인의 로그인 페이지 설정(001 설계). member 체인·회원 화면이 P5 라 그때까지 404 가 된다. 관리자는 `/admin/login` 으로 정상 이동 | P5 | 낮음 | 미정 |
| 2026-07-31 | **fail-fast 가 설계된 동작이 아니라 우연한 부수효과다** — 실측: `PCMS_PII_MASTER_KEY` 를 미주입해도 앱이 정상 기동했다. yml 에 `${VAR}`(기본값 없음)를 써도, 그 프로퍼티를 바인딩하는 `@ConfigurationProperties` 빈이 없으면 Spring 은 placeholder 해석 자체를 시도하지 않는다. 바인딩되는 `gopcms.datasource.*` 조차 부팅은 실패하되 메시지가 `Driver claims to not accept jdbcUrl, ${PCMS_DB_PRIMARY_URL}` 로 **원인이 드러나지 않는다**(placeholder 문자열이 Hikari 까지 그대로 전달) | P0 | **높음** | 미정 — 후보: ① Properties 클래스에 `@Validated`+`@NotBlank` ② 기동 시 필수 환경변수 목록을 검사하는 `EnvironmentPostProcessor`. **P1 에서 방식을 확정**하고 그때 전 Properties 에 일괄 적용 |
| 2026-07-31 | **JODConverter 가 기동 때마다 ERROR 를 남긴다** — `Could not delete '...\.jodconverter_socket_host-...'`. 001 의 `application-local.yml` 주석이 예고한 Windows 파일 잠금 충돌이 실제로 발생. 부팅·health 에는 영향 없고 문서 뷰어(P4) 기능에만 관계된다 | P4 | 낮음 | 미정 — 소음이 거슬리면 local 에서 `gopcms.file.converter.enabled=false` |
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
