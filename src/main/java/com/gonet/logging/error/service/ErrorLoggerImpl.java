package com.gonet.logging.error.service;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.util.IpUtils;
import com.gonet.common.util.SensitiveParamMasker;
import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.error.dto.ErrorLog;
import com.gonet.logging.error.mapper.ErrorLogMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * {@link ErrorLogger} 구현 — log_error INSERT.
 *
 * <p>트랜잭션 정책 (다른 logger 와 동일):
 * <ul>
 *   <li>{@link Propagation#REQUIRES_NEW} — 호출자(원래 응답 TX) 와 분리</li>
 *   <li>{@link LoggingDataSourceConfig#TRANSACTION_MGR} — logging DB</li>
 *   <li>{@code @Async("accessLogExecutor")} — 응답 흐름 비차단 (executor 재사용)</li>
 * </ul>
 *
 * <p>제한:
 * <ul>
 *   <li>error_message: 2000자 cap</li>
 *   <li>stack_trace: 32KB cap (운영 분석에 충분, DB 부담 제한)</li>
 *   <li>request_uri/user_agent/query_string: 500자 cap</li>
 * </ul>
 */
@Service
public class ErrorLoggerImpl extends EgovAbstractServiceImpl implements ErrorLogger {

    private static final Logger log = LoggerFactory.getLogger(ErrorLoggerImpl.class);

    private static final int MAX_MESSAGE_LEN = 2_000;
    private static final int MAX_STACK_LEN   = 32_000;
    private static final int MAX_TEXT_500    = 500;

    private final ErrorLogMapper mapper;

    public ErrorLoggerImpl(ErrorLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Async("accessLogExecutor")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW,
        transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
    public void log(HttpServletRequest req, Throwable ex, Integer statusCode) {
        if (ex == null) return;
        try {
            ErrorLog row = new ErrorLog();
            populateRequest(row, req);
            populateActor(row);
            populateException(row, ex);
            row.setStatusCode(statusCode != null ? statusCode : 500);
            row.setLoggedAt(LocalDateTime.now());
            stampAuditColumns(row);
            mapper.insert(row);
        } catch (Exception logEx) {
            // 격리 — 에러 로그 INSERT 실패가 사용자 응답 흐름을 막아선 안 됨
            log.warn("ERROR_LOG_FAIL errorClass={} reason={}",
                ex.getClass().getName(), logEx.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────

    private static void populateRequest(ErrorLog row, HttpServletRequest req) {
        if (req == null) return;
        row.setRequestUri(cap(req.getRequestURI(), MAX_TEXT_500));
        row.setHttpMethod(req.getMethod());
        row.setQueryString(cap(SensitiveParamMasker.mask(req.getQueryString()), MAX_TEXT_500));
        row.setClientIp(IpUtils.clientIp(req));
        row.setUserAgent(cap(req.getHeader("User-Agent"), MAX_TEXT_500));
        row.setSessionId(shortSessionId(req));
        // micrometer tracing 또는 sleuth/B3
        String traceId = MDC.get("traceId");
        if (traceId == null) traceId = MDC.get("X-B3-TraceId");
        row.setTraceId(traceId);
    }

    private static void populateActor(ErrorLog row) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            row.setActorUserType(AuditContext.ANONYMOUS);
            return;
        }
        if (auth.getPrincipal() instanceof CustomUserDetails ud) {
            row.setActorUserId(ud.getUserId());
            row.setActorUserType(ud.getUserType());
            row.setActorLoginId(ud.getUsername());
        } else {
            row.setActorUserId(auth.getName());
        }
    }

    private static void populateException(ErrorLog row, Throwable ex) {
        row.setErrorClass(ex.getClass().getName());
        row.setErrorMessage(cap(ex.getMessage(), MAX_MESSAGE_LEN));
        row.setStackTrace(cap(stackToString(ex), MAX_STACK_LEN));
    }

    private static String stackToString(Throwable ex) {
        try (StringWriter sw = new StringWriter();
             PrintWriter  pw = new PrintWriter(sw)) {
            ex.printStackTrace(pw);
            return sw.toString();
        } catch (Exception ignore) {
            return ex.toString();
        }
    }

    /** created_by/created_ip/created_at 항상 세팅 — 다른 logger 와 일관. */
    private static void stampAuditColumns(ErrorLog row) {
        String by = (row.getActorUserId() != null && !row.getActorUserId().isBlank())
                    ? row.getActorUserId()
                    : AuditContext.ANONYMOUS;
        row.setCreatedBy(by);
        row.setCreatedIp(row.getClientIp());
        row.setCreatedAt(row.getLoggedAt());
    }

    private static String shortSessionId(HttpServletRequest req) {
        try {
            String sid = (req.getSession(false) == null) ? null : req.getSession(false).getId();
            if (sid == null || sid.length() < 8) return sid;
            return "..." + sid.substring(sid.length() - 8);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String cap(String s, int max) {
        if (s == null) return null;
        return (s.length() <= max) ? s : s.substring(0, max);
    }
}
