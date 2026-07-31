# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 따라야 할 지침입니다.

## 프로젝트

PCMS 2026-003 — eGovFrame 5.0 호환 멀티사이트 웹 CMS.
Java 21(Virtual Threads) / Spring Boot 3.5.9 / MyBatis 전용(JPA 금지) / Thymeleaf + htmx + Tailwind v4 CLI / Spring Security 6.
`war` 패키징(외부 Tomcat 10.1.x + 임베디드 로컬 이중 진입점). DB는 MariaDB 기준 **3개 분리**(primary/secondary/logging).

**전자정부 표준프레임워크 호환성 확인을 받는다** — 아래 "eGov 호환성 (자동 점검됨)" 항목은 임의로 바꾸지 않는다.

## 참조 우선순위 (중요)

1. **[doc/개발가이드.md](doc/개발가이드.md)** — 이 프로젝트의 정본
2. **`D:\claude\pcms2026-001`** — 동작하는 참조 구현. **이식 원본**
3. `D:\claude\pcms2026-001` — 최초 원본

- **`pcms2026-002` 는 코드·문서 모두 참조하지 않는다.** 정리되지 않은 상태로 폐기됐고, 교훈은 개발가이드 §1-2로 흡수했다.
- **문서와 실측이 다르면 실측이 맞다.** (예: 001 문서는 PK를 `varchar(36)`이라 적었으나 실제 DDL은 **`varchar(40)`**)

## 작업 원칙 (가장 중요)

001·002에서 범위가 계속 불어나 되돌린 경험이 있다. 아래를 지킨다.

1. **요청한 범위에서 멈춘다.** 곁다리로 발견한 문제는 고치지 말고 [PLAN.md](doc/PLAN.md) §7 **발견 사항에 기록만** 한다.
2. **플랜에 없는 작업은 하지 않는다.** 필요하면 PLAN 에 항목을 먼저 추가하고 승인받는다.
3. **한 커밋 = 한 주제.** 리팩터링·정리·기능을 한 커밋에 섞지 않는다.
4. **새 의존성·빌드 단계 추가는 사전 확인.** npm 패키지, 번들러, 라이브러리 도입은 먼저 묻는다.
5. **질문은 최소로.** 합리적 기본값이 있으면 그대로 진행하고 가정을 명시한다. 되돌리기 어렵거나 위험한 것만 묻는다.
6. **이식은 실측 그대로.** "개선"은 별도 항목으로 분리한다 — 이식과 리팩터링을 섞으면 문제 원인을 가릴 수 없다.
7. 작업 시작 전 [PLAN.md](doc/PLAN.md)의 현재 페이즈를 확인하고, 완료 시 체크박스를 갱신한다.
8. **이식 시 `Egov` 접두 클래스명은 리네이밍한다** — 호환성 규칙 7. 001의 `EgovXxx`를 그대로 복사하면 위반.

## 정본 문서

| 문서 | 역할 |
|---|---|
| [doc/개발가이드.md](doc/개발가이드.md) | 아키텍처·규약·보안·DB·코드 골격. **규약 질문은 여기부터.** |
| [doc/PLAN.md](doc/PLAN.md) | 페이즈별 작업·DoD·진행 추적·결정 대기·발견 사항 |
| [README.md](README.md) | 빌드·실행·구조 요약 |

**정본 md 외 `.md` 임의 생성 금지** — 사용자 명시 요청 시만.

## 빌드 / 검증

```bash
./mvnw -o compile -DskipTests -Dtailwind.skip=true   # 1차 검증 (매 작업 후)
./mvnw test -Dtest=ArchitectureTest                    # ArchUnit 규약 게이트
./mvnw -o package -DskipTests                          # war 패키징
./mvnw spring-boot:run                                 # 로컬 실행 (local 프로파일)
npm run css:watch                                      # 개발 중 CSS 반복 빌드
```

- **컴파일 통과가 1차 검증 기준.**
- 비밀값 미주입 시 **fail-fast 부팅 실패는 의도된 동작** — `.env.example` 참고.
- Tailwind는 CLI 빌드 필수(CDN 금지). 오프라인 자바 검증만 `-Dtailwind.skip=true`.
- 프런트 규약(인라인 핸들러·raw hex·`${}`) 검사 grep 은 개발가이드 §15.
- **eGov 호환성 검사 grep**(`@Mapper` 잔존·`Egov` 접두 클래스·실행환경 버전)도 개발가이드 §15.

## 버전관리

**git 사용** — 처음부터 이력 관리. 기본 브랜치 `main`.

- **비밀값 커밋 절대 금지.** `.env`는 `.gitignore` 대상.
  `.env.example`은 **키 이름 + `__CHANGE_ME__` 플레이스홀더만** 두고 실제 값은 절대 넣지 않는다
  (001에서 `.env.example`이 `.env` 사본이라 운영 비밀값 21종이 커밋될 뻔한 이력).
- yml에 비밀값 기본값을 박지 않는다 — 기본값 없는 `${VAR}` 로만 주입.
- 커밋 전 `git status` 로 `.env` 스테이징 여부 확인.
- **커밋·푸시는 사용자가 요청할 때만** 수행한다.

## 아키텍처 핵심

- **3-DB 분리**: primary(`tb_*`) / secondary(개별프로그램·외부API) / logging(`log_*`·`stat_*`·`shedlock`).
  각자 DataSource·TxManager·SqlSessionFactory. **`@Transactional` 에 `transactionManager` 반드시 명시**.
- **패키지**: `com.gonet.{config, common, primary, secondary, logging, scheduler}`.
  도메인은 `primary/<domain>/{controller,service,mapper,dto}` 수직 슬라이스.
- **PK**: UUID v7 **`varchar(40)`**. 전 테이블 감사컬럼 6종(created_by/ip/at + updated_by/ip/at), soft-delete `delete_yn`.
- **검색은 이 애플리케이션 밖이다**(2026-07-31) — 외부 검색엔진을 `contextPath=/search` 로 붙인다.
  `tb_search_*` 7종 삭제. **색인 훅·검색어 수집·금지어·동의어·추천어·재색인을 구현하지 않는다.**

## 반드시 지킬 규약

- **Service**: 인터페이스 + `EgovAbstractServiceImpl` 상속(**호환성 규칙 5 — 예외 없음**).
  직접 상속이 곤란하면 `EgovAbstractServiceImpl` 을 상속한 **공통 추상 서비스**를 경유한다(간접 상속도 인정됨).
  클래스 레벨 `@Transactional(readOnly=true, transactionManager=…)` 기본,
  **쓰기 메서드는 반드시 writable override**. 생성자 주입.
- **Mapper**: **`@EgovMapper`** 인터페이스 + XML **`*_maria.xml` 단일**(MariaDB 전용).
  **MyBatis `@Mapper`·`@MapperScan` 금지** — `@Mapper` 는 실행환경 v4.3 이하 표기라 5.0 기준 위반이다.
  스캔은 **DataSource 3개별 `MapperConfigurer` 빈**(호환성 규칙 5).
  namespace ↔ FQN 1:1. **전량 `#{}` 바인딩, `${}` 절대 금지**(SQLi). LIKE 와일드카드 이스케이프.
- **Controller 접미사**: `ApiController`(REST `/api/**`) / `UsrController`(사용자) / `MngController`(관리자 `/admin/**`).
  Mapper 직접 호출 금지 — Service 경유. CUD는 try-catch + log + flash + `HX-Redirect`.
- **새 URL 추가 시 `tb_role_url_access` 접근 규칙 등록 필수** — 무매칭 DENY라 빠뜨리면 화면이 안 열린다.
- **공통 자원 재사용 우선** — `common/`(페이징·마스킹·감사·암호화·파일) 활용, 도메인 중복 구현 금지. Lombok 전면 사용.
- **`application.yml` 주석 = 운영 정책 문서** — 변경 전 해당 키 주석 확인, 변경 시 주석도 갱신.

## eGov 호환성 (자동 점검됨)

근거: 「전자정부 표준프레임워크 호환성 가이드」(2026-06-22) 규칙 1~7. 상세는 개발가이드 §2 "호환성 인증 요건".

- **실행환경 필수 4종**을 `org.egovframe.rte` 에서 **동일 버전(5.0.0)** 으로 적용:
  `egovframe-rte-ptl-mvc` · `-fdl-cmmn` · `-psl-dataaccess` · `-fdl-logging`.
  rte jar 는 **변경 금지**(원본 해시 동일). 확장은 상속으로만.
- **Spring 버전 상한**: 5.0 기준선 Spring Boot **3.5.6**(Spring 6.2.11 / Security 6.5.5 / MyBatis 3.5.19).
  현 지정 **3.5.9** 는 패치 상향이라 허용. **3.6.x / 4.x 로 올리면 즉시 위반.** Java 21은 "JDK 17+" 충족.
- **Mapper 는 `@EgovMapper` + `MapperConfigurer`** — MyBatis `@Mapper`/`@MapperScan` 금지.
- **Service 는 `EgovAbstractServiceImpl` 상속 + 인터페이스 구현** — 예외 없음.
- **Controller 는 DAO·NoSQL·MQ·Cache 직접 호출 금지** — 주입된 Service 경유.
- **rte 클래스를 상속한 클래스는 이름이 `Egov` 로 시작 금지**, `org.egovframe.rte` 패키지에 정의 금지.
- **primary·secondary·logging 세 계층 모두** 같은 버전·아키텍처·명명규칙(규칙 8).
- **Flyway 는 충돌 없음** — 단 `@Service`/`@Repository` 로 감싸지 말 것(규칙 4·5 오탐).
  `@Configuration` 안의 `Flyway` 빈 정의로만 유지한다.

## 트랜잭션 함정 (실제 장애 이력)

- 클래스 레벨 `readOnly=true`는 메서드에 **상속**됨 — 쓰기 override 누락 시 "Connection is read-only" 또는 조용한 롤백.
- **자기호출** `this.txMethod()`는 프록시 우회로 `@Transactional`·`@Async` **무시** — 별도 빈 분리 또는 `@Lazy self` 주입.
- 긴 외부호출(@Async 업로드·폴링)은 `NOT_SUPPORTED`/`REQUIRES_NEW`로 격리.
- 로그 기록은 `REQUIRES_NEW` — 본 트랜잭션이 롤백돼도 남아야 한다.
- 애노테이션 변경은 hot-swap 불가 — 완전 정지 → Rebuild → Run. **DevTools 금지**(Virtual Threads+HikariCP Windows JVM 크래시 이력).

## UI / 프런트엔드

- **KRDS 시맨틱 토큰만**: `bg-surface`·`text-fg-subtle`·`border-line`·`bg-brand-50`.
  **raw hex·Tailwind 기본색(`bg-blue-500`)·기본 타이포(`text-xl`) 금지.**
  타이포는 `text-{display|heading|body|label}-*`, **굵기 400/500/700만**(600 금지), radius 최대 12px.
  예외: 메일 템플릿은 raw hex 허용(이메일 클라이언트가 CSS 변수 미지원).
- **htmx + 순수 자바스크립트만 — JS 프레임워크 금지.** 이벤트 위임(document 1회 등록 + `closest('[data-action]')`),
  `htmx:load`에서 멱등 재초기화(`data-initialized` 가드).
- **인라인 `on*=` 핸들러 절대 금지.** `script-src`에 `'unsafe-inline'`이 없어 브라우저가 **조용히 무시**한다 —
  001에서 `onsubmit="return confirm(…)"` 38건이 무력화돼 **삭제 확인창 없이 삭제가 실행**되던 이력.
  대체 계약은 개발가이드 §9-2 표(`data-confirm`, `submit-on-change`, `data-history-back`, `dialog-open/close`, 이미지 폴백 등).
- **`strict-dynamic` 하에서는 host 화이트리스트가 무시된다** — CDN `<script src>`는 차단.
  외부 `.js` + CSP nonce, **self-host 필수**.
- **정적 자원 permitAll 누락 주의** — `/css/**`·`/js/**`·`/fonts/**`·`/img/**`·`/tmpl/**`
  (001에서 `/fonts/**` 누락으로 폰트가 조용히 시스템 폰트로 폴백된 이력).
- 네이티브 요소 우선: 아코디언 `<details>/<summary>`, 모달 `<dialog>`.
- **위지윅 에디터는 공통 모듈 경유** — 엔진 **tiptap(기본) / Namo CrossEditor 4** 2종.
  사용처는 `<textarea data-editor>`, 화면 지정은 `data-editor="tiptap|namo"`.
  값은 원본 textarea 로 동기화되므로 컨트롤러·DTO 변경 불필요.
  **에디터 산출 HTML은 신뢰 입력이 아니다 — 저장 경로에서 OWASP Sanitizer 필수.**

## 보안 (웹쉘 침해 대응 경험 반영 — 보수적으로)

- 다중 SecurityFilterChain: admin(Order 10, IP 화이트리스트+2FA) / member(20) / default(100).
  세션 `PCMS_SID`, `changeSessionId()`, `maximumSessions(1)`.
- 인가는 DB 기반 RBAC — `tb_role_url_access` + `DynamicAuthorizationManager`(priority ASC, **무매칭 DENY**).
- 회원 인가: `tb_member_role` 미사용 — `AUTHENTICATED` + `user_type=MEMBER`. 통합 로그인 `v_user_login` VIEW.
- **로그인 주체는 `MEMBER`·`STAFF`(관리자) 2종뿐** — 직원(`EMPLOYEE`)은 제외(2026-07-31).
  `tb_employee` 는 **조회 전용**이며 로그인·권한을 부여하지 않는다.
- **PII 이름 정책**: `tb_admin.admin_name` **평문** / `tb_admin_withdraw.admin_name`·`tb_member_withdraw.member_name` **마스킹 저장**
  (탈퇴 테이블은 ID·입증정보가 부족해 이름을 남기되 마스킹한다).
- 파일 업로드 6중 방어(확장자/Tika 매직바이트/격리/재인코딩/FIM/ClamAV) + 경로 containment 검사.
- CSRF(XSRF-TOKEN 쿠키), CSP nonce(+HTML `no-store`), OWASP Sanitizer, 로그인 잠금(5회/30분), Bucket4j.
- **JSON 직렬화 시 HTML 이스케이프**(`HtmlSafeJson`) — 001에서 검색어 저장형 XSS 실제 발생.
- PII: `@Encrypt`(AES-256-GCM, `{AG}` 프리픽스) + `MaskUtils` 마스킹 + `log_privacy_access` 기록. 마스터키 fail-fast.

## DB / 마이그레이션

**Flyway 사용.** 스키마 변경은 마이그레이션 파일로 관리한다. **코드에서 임의 DDL 실행 금지.**

- 위치: `db/migration/{primary,secondary,logging}/{vendor}/`, 네이밍 `V{yyyyMMdd}{NN}__{설명}.sql` / `R__{설명}.sql`.
- `spring.datasource`가 아니라 커스텀 DataSource 3개라 **자동설정 불가** — DataSource별 Flyway 빈 명시 구성.
- **적용된 마이그레이션 파일은 수정하지 않는다**(체크섬 불일치로 기동 실패) — 새 버전을 추가한다.
- **시드 데이터는 마이그레이션에 넣지 않는다**(데모 데이터가 운영에 딸려 가면 안 됨). 단, 참조 데이터(공통코드·기본 접근규칙)는 포함.
- 신규 테이블은 UUID v7 PK + 감사컬럼 6종 + **MariaDB DDL**. 상세는 개발가이드 §6.

## 배치 / 스케줄러

`com.gonet.scheduler`, ShedLock(logging DB `shedlock`). 잡 8종과 cron 은 개발가이드 §12 표 참조.
cron 은 `${…:기본값}` 으로 외부화하고 **dry-run 플래그**를 둔다.

- **Google GenAI Java SDK(`com.google.genai:google-genai`) 는 도입하지 않는다**(2026-07-31).
  001 의 `GeminiFileRenewScheduler`·`GeminiFileService` 계열은 **이식 대상에서 제외**.
  AI 기능은 추후 **context 방식으로 별도 개발**한다.

## 참고

- NICE 본인인증 jar는 JPMS 플래그 필요(`--add-exports/--add-opens java.base/com.sun.crypto.provider=ALL-UNNAMED`)
  — surefire·`spring-boot:run`·운영 Tomcat setenv **모두**. 누락 시 본인인증에서만 500.
- Thymeleaf 3.1+ 에서 `#httpServletRequest`·`#request` 사용 불가 — `@ControllerAdvice` 로 모델 주입.
- war 는 표준 war 로 배포(spring-boot repackage 안 함 — BOOT-INF 중첩은 외부 Tomcat ClassLoader 와 비호환).
