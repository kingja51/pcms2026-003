#!/bin/sh
# ============================================================================
#  PCMS 2026-003 — 외부 Tomcat 10.1.x setenv (Linux/macOS)
# ----------------------------------------------------------------------------
#  설치 위치: $CATALINA_BASE/bin/setenv.sh   (chmod 750)
#  Tomcat 이 startup 시 자동으로 source 한다. 여기서 하는 일:
#    1) 비밀·환경변수를 별도 파일(conf/pcms.env)에서 로드 — 스크립트에 secret 없음
#    2) NICE 본인인증 jar 용 JPMS 플래그 + 운영 프로파일 + JVM 옵션
#
#  ⚠ 비밀값 미주입 시 앱은 fail-fast 로 부팅에 실패한다(의도된 동작).
#     RequiredPropertyValidator 가 누락 키를 전부 나열하므로 그 목록을 보고 채운다.
#     → conf/pcms.env 를 pcms.env.example 기준으로 채우고 chmod 600 할 것.
# ============================================================================

# ── 1) 환경변수 파일 로드 ──────────────────────────────────────────────────
#  기본 경로: $CATALINA_BASE/conf/pcms.env. PCMS_ENV_FILE 로 override 가능.
#
#  source 를 쓰지 않고 라인 단위로 읽는 이유: 값에 & ( ) $ 같은 문자가 있으면
#  셸이 해석해 버린다. DB 비밀번호에 & 하나만 있어도 조용히 깨진다.
PCMS_ENV_FILE="${PCMS_ENV_FILE:-$CATALINA_BASE/conf/pcms.env}"
_CR=$(printf '\r')                               # POSIX-safe CR (dash 는 $'\r' 미지원)
if [ -f "$PCMS_ENV_FILE" ]; then
  while IFS= read -r _line || [ -n "$_line" ]; do
    _line="${_line%$_CR}"                        # CRLF 대비 (끝 CR 제거)
    case "$_line" in ''|\#*) continue ;; esac    # 빈 줄·주석 skip
    [ "${_line#*=}" = "$_line" ] && continue     # '=' 없는 줄 skip
    _key="${_line%%=*}"
    _val="${_line#*=}"
    export "$_key=$_val"
  done < "$PCMS_ENV_FILE"
  echo "[setenv] loaded env from $PCMS_ENV_FILE"
else
  echo "[setenv] WARN: $PCMS_ENV_FILE 없음 — 비밀값 미주입 시 부팅 실패(fail-fast)" >&2
fi

# ── 2) 운영 프로파일 ───────────────────────────────────────────────────────
#  prod: Swagger 비활성, 정적 리소스 immutable 캐시, 보수적 로깅,
#        Flyway 비활성 고정(스키마 변경은 DBA 가 CLI 로 집행 — D1 ③).
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
export SPRING_PROFILES_ACTIVE

# ── 3) NICE 본인인증 JPMS 플래그 (필수) ────────────────────────────────────
#  NiceID_v1.1.jar 가 java.base 의 com.sun.crypto.provider.SunJCE 내부 클래스를
#  리플렉션으로 건드린다. Java 17+ 강한 캡슐화 때문에 플래그가 없으면
#  InaccessibleObjectException 이 난다.
#
#  ⚠ 증상이 고약하다: **부팅은 성공하고 본인인증 요청에서만 500** 이 난다.
#     그래서 배포 리허설에서 안 잡히고 운영에서 처음 드러난다.
#     surefire · spring-boot:run · 여기 세 곳 모두 같은 값이어야 한다(pom 의 nice.jvm.args).
NICE_JPMS_OPTS="--add-exports=java.base/com.sun.crypto.provider=ALL-UNNAMED --add-opens=java.base/com.sun.crypto.provider=ALL-UNNAMED"

# ── 4) JVM 옵션 ────────────────────────────────────────────────────────────
#  · Java 21 + Virtual Threads — GC 는 G1 기본 유지(대량 VT 에 무난).
#  · 힙은 서버 사양에 맞춰 조정. 아래는 4GB RAM 기준 보수값.
#  · UTF-8·KST 고정, headless(서버에 GUI 없음).
JVM_MEM_OPTS="${JVM_MEM_OPTS:--Xms512m -Xmx2048m}"
JVM_SYS_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Duser.timezone=Asia/Seoul -Djava.awt.headless=true"

# ── 5) 조립 ────────────────────────────────────────────────────────────────
#  기존 CATALINA_OPTS 를 보존하며 뒤에 append 한다.
CATALINA_OPTS="$CATALINA_OPTS \
 -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE \
 $NICE_JPMS_OPTS \
 $JVM_MEM_OPTS \
 $JVM_SYS_OPTS"
export CATALINA_OPTS

echo "[setenv] profile=$SPRING_PROFILES_ACTIVE  mem='$JVM_MEM_OPTS'"
echo "[setenv] NICE JPMS flags applied"
