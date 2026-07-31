# PCMS 2026-003

**eGovFrame 5.0 호환 멀티사이트 웹 CMS.** 하나의 백엔드로 여러 사이트를 운영하고, 사이트마다 다른 디자인 언어를 입힌다.

> 규약·아키텍처 정본은 [doc/개발가이드.md](doc/개발가이드.md), 진행 관리는 [doc/PLAN.md](doc/PLAN.md), 에이전트 작업 지침은 [CLAUDE.md](CLAUDE.md).

---

## 현재 상태

**설계·계획 단계.** 저장소에는 아직 문서만 있다. 구현은 [doc/PLAN.md](doc/PLAN.md) 의 **P0** 부터 시작한다.

선행 프로젝트 `pcms2026-001`(v0.1.0 — Java 797개·매퍼 XML 252개·템플릿 377개, 15개 도메인 동작)이
**참조 구현**으로 남아 있으며, 003은 그 자산을 **플랜 순서에 맞춰 선별 이식**한다.

| 참조 우선순위 | 대상 |
|---|---|
| 1 | [doc/개발가이드.md](doc/개발가이드.md) — 이 프로젝트 정본 |
| 2 | `D:\claude\pcms2026-001` — 동작하는 참조 구현(이식 원본) |
| 3 | `D:\claude\pcms2026-001` — 최초 원본 |

`pcms2026-002` 는 **참조하지 않는다**(정리되지 않은 상태로 폐기, 교훈은 개발가이드 §1-2로 흡수).

## 기술 스택

| 영역 | 채택 |
|---|---|
| 런타임 | Java 21 (Virtual Threads) — **DevTools 금지** |
| 프레임워크 | Spring Boot 3.5.9, Spring Security 6, eGovFrame 5.0 |
| 영속 | **MyBatis 전용** (JPA 금지), XML 매퍼 `*_maria.xml` 단일(MariaDB 전용) |
| DB | MariaDB 기준 — **3개 분리**: primary / secondary / logging |
| 마이그레이션 | **Flyway** |
| 뷰 | Thymeleaf + htmx + **순수 JavaScript** (JS 프레임워크 금지) |
| 스타일 | **Tailwind v4 CLI** (CDN 금지) + KRDS 디자인 시스템 |
| 에디터 | **tiptap**(기본) / **Namo CrossEditor 4** |
| 배치 | Spring Scheduler + ShedLock |
| 패키징 | `war` (외부 Tomcat 10.1.x + 임베디드 로컬 이중 진입점) |

## 아키텍처 핵심

- **3-DB 분리** — primary(`tb_*`) / secondary(개별프로그램·외부API) / logging(`log_*`·`stat_*`·`shedlock`).
  각자 DataSource·TxManager·SqlSessionFactory. `@Transactional` 에 `transactionManager` 명시 필수.
- **패키지** — `com.gonet.{config, common, primary, secondary, logging, scheduler}`.
  도메인은 `primary/<domain>/{controller,service,mapper,dto}` 수직 슬라이스.
- **PK** — UUID v7 `varchar(40)`. 전 테이블 감사컬럼 6종, soft-delete `delete_yn`.
- **컨트롤러 접미사** — `ApiController`(`/api/**`) / `UsrController`(사용자) / `MngController`(`/admin/**`).
- **인가** — DB 기반 RBAC(`tb_role_url_access` + `DynamicAuthorizationManager`, **무매칭 DENY**).
  다중 SecurityFilterChain(admin/member/default).

## 디렉터리 구조

```
pcms2026-003/
├── CLAUDE.md, AGENTS.md, README.md      # 루트 고정(도구가 루트에서만 인식)
├── doc/개발가이드.md, doc/PLAN.md
├── pom.xml, package.json, .env.example, .gitignore
├── lib/                                  # 로컬 의존 jar (NiceID 등)
├── src/
│   ├── krds.css                          # Tailwind v4 입력(KRDS 토큰)
│   └── main/
│       ├── java/com/gonet/{config,common,primary,secondary,logging,scheduler}/
│       └── resources/
│           ├── application*.yml           # 주석 = 운영 정책 문서
│           ├── db/migration/{primary,secondary,logging}/{vendor}/
│           ├── mapper/…                   # *_maria.xml
│           ├── templates/{fragments,admin,front,mail}/
│           └── static/{css,js,fonts,img}/
├── sql/{mariadb,mysql,postgres}/         # 시드·참고 DDL
└── deploy/tomcat/                        # setenv, pcms.env.example
```

## 최초 세팅

`.env` 와 빌드 산출물은 저장소에 없다.

```bash
cp .env.example .env      # 실제 값으로 채운다 (커밋 금지, .gitignore 대상)
npm install && npm run css
./mvnw -o compile -DskipTests -Dtailwind.skip=true
```

- `.env` 미작성 시 **fail-fast 부팅 실패는 의도된 동작**이다.
- 로컬 DB 3종(primary / secondary / logging) 준비가 선행돼야 한다.

## 빌드 / 실행

```bash
./mvnw -o compile -DskipTests -Dtailwind.skip=true   # 1차 검증(오프라인 자바 컴파일)
./mvnw test -Dtest=ArchitectureTest                    # ArchUnit 규약 게이트
./mvnw -o package -DskipTests                          # war 패키징
./mvnw spring-boot:run                                 # 로컬 실행 (local 프로파일)

npm run css          # Tailwind output.css 1회 빌드
npm run css:watch    # 개발 중 CSS 반복 빌드
```

- **컴파일 통과 = 1차 검증 기준.**
- Tailwind는 CLI 빌드 필수. 오프라인 자바 검증만 `-Dtailwind.skip=true`.
- 로컬 포트 기본 8080. 점유 시 `--server.port=8090`.
- 프런트 규약 검사(인라인 핸들러·raw hex·`${}`·매퍼 파일명) grep 은 [개발가이드 §15](doc/개발가이드.md#15-검증-게이트).

## 프로파일

| 프로파일 | 용도 | 특징 |
|---|---|---|
| `local` | 개발(기본) | 쿠키 secure=false, Thymeleaf 캐시 off, 정적 리소스 **no-store**(F5만으로 CSS 반영) |
| `dev` | 통합 | 프록시/터널 뒤 |
| `prod` | 운영 | Swagger 비활성, tracing 샘플링 축소, 정적 리소스 1년 immutable + fingerprint |

`application.yml` 의 **한글 주석은 운영 정책 문서**다 — 설정 변경 전 해당 키 주석을 먼저 읽는다.

## 보안 요약

- 다중 SecurityFilterChain: admin(IP 화이트리스트+2FA) / member / default. 세션 `PCMS_SID`, `changeSessionId()`, `maximumSessions(1)`.
- 파일 업로드 6중 방어(확장자/Tika 매직바이트/격리/재인코딩/FIM/ClamAV) + 경로 containment 검사.
- CSRF, **CSP nonce**, OWASP Sanitizer, 로그인 잠금(5회/30분), Bucket4j.
- PII: `@Encrypt`(AES-256-GCM, `{AG}` 프리픽스) + `MaskUtils` 마스킹 + `log_privacy_access` 기록. 마스터키 fail-fast.
- **`script-src` 에 `'unsafe-inline'` 이 없다** — 인라인 `on*=` 핸들러는 브라우저가 **조용히 무시**하므로 절대 쓰지 않는다
  ([개발가이드 §9-2](doc/개발가이드.md#9-2-javascript--csp)).

## DB / 마이그레이션

**Flyway 사용.** `db/migration/{primary,secondary,logging}/{vendor}/` 에 `V{yyyyMMdd}{NN}__{설명}.sql`.

- `spring.datasource` 가 아니라 커스텀 DataSource 3개라 **자동설정 불가** — DataSource별 Flyway 빈을 명시 구성한다.
- **적용된 마이그레이션 파일은 수정하지 않는다**(체크섬 불일치로 기동 실패) — 새 버전을 추가한다.
- **시드 데이터는 마이그레이션에 넣지 않는다.** 단, 참조 데이터(공통코드·기본 접근규칙)는 포함.

## 배치 / 스케줄러

`com.gonet.scheduler` + ShedLock(logging DB). 잡 9종 — 회원 휴면(01:00) / 접속통계(02:00, 10분) /
로그 보존(03:30) / 파일 퍼지(04:00) / soft-delete 정리(04:30) / 탈퇴 파기(04:45) / 기상 수집(20분) / AI 파일 갱신(6시간).
상세는 [개발가이드 §12](doc/개발가이드.md#12-배치--스케줄러).

## 외부 Tomcat 배포

배포 자산은 `deploy/tomcat/` — `setenv.sh`(Linux)·`setenv.bat`(Windows)·`pcms.env.example`.

`setenv` 가 자동 처리: **NICE 본인인증 JPMS 플래그**, `spring.profiles.active=prod`,
`conf/pcms.env` 환경변수 로드, UTF-8·`Asia/Seoul`·headless·힙.

war 는 **표준 war 로 배포**한다(spring-boot repackage 안 함 — BOOT-INF 중첩은 외부 Tomcat ClassLoader 와 비호환).
비밀값은 스크립트에 넣지 않고 `conf/pcms.env`(권한 600)로 분리한다.

## 버전관리

**git 사용** — 처음부터 이력 관리, 기본 브랜치 `main`.

- **비밀값 커밋 금지** — `.env` 는 `.gitignore` 대상.
  `.env.example` 에는 **키 이름 + `__CHANGE_ME__` 플레이스홀더만** 둔다.
- yml 에 비밀값 기본값을 박지 않는다 — 기본값 없는 `${VAR}` 만 사용.
- 커밋 전 `git status` 로 `.env` 스테이징 여부를 확인한다.

## 문서

| 문서 | 역할 |
|---|---|
| [CLAUDE.md](CLAUDE.md) · [AGENTS.md](AGENTS.md) | 에이전트 작업 지침 — 헤더 외 내용 동일, **편집 시 양쪽 동기화** |
| [doc/개발가이드.md](doc/개발가이드.md) | 아키텍처·규약·보안·DB·코드 골격 **정본** |
| [doc/PLAN.md](doc/PLAN.md) | 페이즈별 작업·DoD·진행 추적·결정 대기·발견 사항 |
