package com.gonet.primary.system.login.controller;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.logging.log.dto.LoginLog;
import com.gonet.logging.log.mapper.LoginLogMapper;
import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityEventType;
import com.gonet.logging.security.service.SecurityLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 로그인 실패 핸들러 공통 베이스 — log_login + audit + 분류 + 리다이렉트.
 *
 * <p>구체 클래스는 {@link #failureRedirectBase()} 만 정의 (예: /admin/login, /member/login).
 * <p>잠금 정책: {@link #FAIL_LIMIT}회 실패 시 {@link #LOCK_MINUTES}분 자동 잠금.
 *
 * <p><b>User enumeration 방지</b>: 리다이렉트 URL 은 항상
 * {@code ?error=<reasonCode>} 1개 파라미터만 포함한다. 실패 카운터·잠금 플래그
 * 같은 사용자별 상태는 URL 노출 대신 <b>세션 속성</b>({@link #SESS_FAIL_COUNT}/{@link #SESS_LOCKED}/
 * {@link #SESS_LOCK_MINUTES}) 에 기록되어 로그인 컨트롤러가 1회 소비한다.
 * 이로써 "존재하지 않는 ID" 와 "존재하지만 비밀번호 오류" 응답이 동일하게 보인다.
 *
 * <p>타이밍 공격은 {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}
 * 의 {@code mitigateAgainstTimingAttack} 이 자동 수행 (user-not-found 경로에도 BCrypt 더미 compare).
 */
public abstract class AbstractLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    protected static final Logger log = LoggerFactory.getLogger(AbstractLoginFailureHandler.class);

    /** 자동 잠금 임계값 (이 횟수째 실패 시 잠금 발동). */
    public static final int FAIL_LIMIT   = 5;
    /** 잠금 지속 시간(분). */
    public static final int LOCK_MINUTES = 10;

    /** 세션 키 — 로그인 실패 상세. 다음 GET /login 에서 1회 소비 후 삭제. */
    public static final String SESS_FAIL_COUNT   = "PCMS_LOGIN_FAIL_COUNT";
    public static final String SESS_LOCKED       = "PCMS_LOGIN_LOCKED";
    public static final String SESS_LOCK_MINUTES = "PCMS_LOGIN_LOCK_MINUTES";

    protected final LoginLogMapper loginLogMapper;
    protected final AuditLogger    auditLogger;
    protected final SecurityLogger securityLogger;

    protected AbstractLoginFailureHandler(LoginLogMapper loginLogMapper,
                                           AuditLogger auditLogger,
                                           SecurityLogger securityLogger) {
        this.loginLogMapper = loginLogMapper;
        this.auditLogger = auditLogger;
        this.securityLogger = securityLogger;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest req,
                                        HttpServletResponse res,
                                        AuthenticationException ex) throws IOException, ServletException {
        String loginId = req.getParameter("loginId");
        String reasonCode = classify(ex);
        String result = "LOCKED".equals(reasonCode) ? LoginLog.RESULT_LOCKED : LoginLog.RESULT_FAIL;

        LoginLog entry = new LoginLog();
        entry.setLoginId(loginId);
        entry.setClientIp(AuditContext.ip());
        entry.setUserAgent(req.getHeader("User-Agent"));
        entry.setResult(result);
        entry.setFailReason(reasonCode);
        entry.setCreatedBy(AuditContext.userId());
        entry.setCreatedIp(AuditContext.ip());
        entry.setUpdatedBy(AuditContext.userId());
        entry.setUpdatedIp(AuditContext.ip());
        try {
            loginLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("log_login insert failed (FAIL): {}", e.getMessage());
        }
        auditLogger.write(AuditEvent.of("LOGIN", "auth")
            .withActor(null, null, loginId)
            .withHttp(req.getMethod(), req.getRequestURI(), AuditContext.ip(), req.getHeader("User-Agent"))
            .withResult("FAIL")
            .withAfter("{\"reason\":" + JsonUtils.quote(reasonCode) + "}"));
        log.info("===LOGIN_FAIL form={} loginId={} ip={} reason={}",
            failureRedirectBase(), loginId, AuditContext.ip(), reasonCode);

        // 실패 상세(카운트/잠금) — 세션에 저장, 다음 폼 렌더링에서 1회 소비
        FailureDetails d = captureFailureDetails(req, loginId, reasonCode);
        HttpSession session = req.getSession(true);
        if (d.failCount() != null && d.failCount() > 0) {
            session.setAttribute(SESS_FAIL_COUNT, d.failCount());
        }
        if (d.locked()) {
            session.setAttribute(SESS_LOCKED, Boolean.TRUE);
            if (d.lockMinutes() != null) session.setAttribute(SESS_LOCK_MINUTES, d.lockMinutes());
            // 보안 이벤트 분리 적재 (§0.48) — 잠금 발동/잠금 상태 재시도
            try {
                securityLogger.write(SecurityEvent.of(SecurityEventType.LOGIN_LOCK)
                    .withDetail("{\"loginId\":" + JsonUtils.quote(loginId)
                        + ",\"form\":" + JsonUtils.quote(failureRedirectBase())
                        + ",\"reason\":" + JsonUtils.quote(reasonCode)
                        + (d.lockMinutes() != null
                            ? ",\"lockMinutes\":" + d.lockMinutes()
                            : "")
                        + "}"));
            } catch (Exception ignored) {
                // 보안 적재 실패가 로그인 응답을 막지 않도록 무시 — fallback logger 가 흡수.
            }
        }

        String redirect = failureRedirectBase()
                        + "?error=" + URLEncoder.encode(reasonCode, StandardCharsets.UTF_8);
        setDefaultFailureUrl(redirect);
        super.onAuthenticationFailure(req, res, ex);
    }

    /** 실패 시 돌아갈 폼 경로 (예: {@code /admin/login}, {@code /member/login}). */
    protected abstract String failureRedirectBase();

    /**
     * 도메인별 실패 상세(실패 카운트 증가·잠금 판단 등 DB 부작용 포함) 를 반환.
     * 반환된 값은 본 핸들러가 세션에 기록하며, URL 에는 노출되지 않는다.
     * 기본 구현은 {@link FailureDetails#none()} — 추가 정보 없음.
     */
    protected FailureDetails captureFailureDetails(HttpServletRequest req, String loginId, String reasonCode) {
        return FailureDetails.none();
    }

    /** 로그인 실패 후 폼에 노출할 상세 정보. {@code null} 필드는 미노출. */
    public record FailureDetails(Integer failCount, boolean locked, Integer lockMinutes) {
        public static FailureDetails none() { return new FailureDetails(null, false, null); }
        public static FailureDetails lockedNow(int lockMinutes) { return new FailureDetails(null, true, lockMinutes); }
        public static FailureDetails count(int n) { return new FailureDetails(n, false, null); }
    }

    private String classify(AuthenticationException ex) {
        if (ex instanceof LockedException)            return "LOCKED";
        if (ex instanceof DisabledException)          return "DISABLED";
        if (ex instanceof UsernameNotFoundException)  return "INVALID_CREDENTIALS";
        if (ex instanceof BadCredentialsException)    return "INVALID_CREDENTIALS";
        return "LOGIN_FAILED";
    }
}
