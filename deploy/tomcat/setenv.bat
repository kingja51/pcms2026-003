@echo off
rem ==========================================================================
rem  PCMS 2026-003 - 외부 Tomcat 10.1.x setenv (Windows)
rem --------------------------------------------------------------------------
rem  설치 위치: %CATALINA_BASE%\bin\setenv.bat
rem  Tomcat 이 startup.bat 실행 시 자동 호출한다.
rem
rem  setenv.sh 와 동일한 일을 한다:
rem    1) conf\pcms.env 에서 환경변수 로드 - 스크립트에 secret 없음
rem    2) NICE JPMS 플래그 + 운영 프로파일 + JVM 옵션
rem
rem  주의: 이 파일은 CP949 가 아니라 ASCII 주석만 쓴다. Tomcat 이 실행하는
rem        cmd 의 코드페이지가 환경마다 달라 한글 주석이 깨지면 파싱까지
rem        틀어지는 경우가 있다.
rem ==========================================================================

setlocal enabledelayedexpansion

rem -- 1) 환경변수 파일 로드 ------------------------------------------------
rem    "KEY=value" 형식. 빈 줄과 # 주석은 건너뛴다.
rem    delims= 를 비워 값 안의 공백/특수문자를 그대로 살린다.
if "%PCMS_ENV_FILE%"=="" set "PCMS_ENV_FILE=%CATALINA_BASE%\conf\pcms.env"

if exist "%PCMS_ENV_FILE%" (
  for /f "usebackq eol=# tokens=1,* delims== " %%A in ("%PCMS_ENV_FILE%") do (
    if not "%%A"=="" set "%%A=%%B"
  )
  echo [setenv] loaded env from %PCMS_ENV_FILE%
) else (
  echo [setenv] WARN: %PCMS_ENV_FILE% not found - app will fail-fast without secrets 1>&2
)

rem -- 2) 운영 프로파일 ----------------------------------------------------
if "%SPRING_PROFILES_ACTIVE%"=="" set "SPRING_PROFILES_ACTIVE=prod"

rem -- 3) NICE 본인인증 JPMS 플래그 (필수) ---------------------------------
rem    없으면 부팅은 되고 본인인증 요청에서만 500 이 난다.
set "NICE_JPMS_OPTS=--add-exports=java.base/com.sun.crypto.provider=ALL-UNNAMED --add-opens=java.base/com.sun.crypto.provider=ALL-UNNAMED"

rem -- 4) JVM 옵션 ---------------------------------------------------------
if "%JVM_MEM_OPTS%"=="" set "JVM_MEM_OPTS=-Xms512m -Xmx2048m"
set "JVM_SYS_OPTS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Duser.timezone=Asia/Seoul -Djava.awt.headless=true"

rem -- 5) 조립 -------------------------------------------------------------
set "CATALINA_OPTS=%CATALINA_OPTS% -Dspring.profiles.active=%SPRING_PROFILES_ACTIVE% %NICE_JPMS_OPTS% %JVM_MEM_OPTS% %JVM_SYS_OPTS%"

echo [setenv] profile=%SPRING_PROFILES_ACTIVE%  mem=%JVM_MEM_OPTS%
echo [setenv] NICE JPMS flags applied

endlocal & set "CATALINA_OPTS=%CATALINA_OPTS%" & set "SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%"
