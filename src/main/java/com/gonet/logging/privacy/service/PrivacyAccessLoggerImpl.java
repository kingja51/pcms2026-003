package com.gonet.logging.privacy.service;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.util.IpUtils;
import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.privacy.dto.PrivacyAccessEvent;
import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import com.gonet.logging.privacy.mapper.PrivacyAccessLogMapper;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * {@link PrivacyAccessLogger} 구현 — log_privacy_access INSERT.
 *
 * <p>설계 포인트:
 * <ul>
 *   <li>{@code @Async("accessLogExecutor")} — 응답 commit 직후 백그라운드 실행
 *       (별도 executor 추가 부담을 피해 access 로그 executor 재사용)</li>
 *   <li>{@code REQUIRES_NEW} + {@code loggingTransactionManager} — 호출자 TX 와 분리</li>
 *   <li>actor / IP / HTTP 정보는 호출자 스레드에서 미리 보강 후 DTO 에 박아 비동기로 넘김
 *       (워커 스레드는 SecurityContext 가 비어 있을 수 있음 — AccessLoggerImpl 의 같은 회귀)</li>
 * </ul>
 *
 * <p>적재 실패는 WARN 로그만 — PIPA 적재 실패가 사용자 응답을 막아선 안 되지만,
 * 모니터링 알람(ELK/Prometheus) 으로 후속 조치한다.
 */
@Service
public class PrivacyAccessLoggerImpl extends EgovAbstractServiceImpl implements PrivacyAccessLogger {

    private static final Logger log = LoggerFactory.getLogger(PrivacyAccessLoggerImpl.class);

    private final PrivacyAccessLogMapper mapper;

    public PrivacyAccessLoggerImpl(PrivacyAccessLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void write(PrivacyAccessEvent event) {
        if (event == null) return;
        // 호출자 스레드에서 actor/HTTP 보강 — 비동기 워커는 SecurityContext 비어있을 수 있음
        autofill(event);
        PrivacyAccessLog row = toRow(event);
        writeAsync(row);
    }

    @Async("accessLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW,
                   transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
    public void writeAsync(PrivacyAccessLog row) {
        try {
            mapper.insert(row);
        } catch (Exception ex) {
            log.warn("PRIVACY_ACCESS_LOG_FAIL action={} entity={} reason={}",
                row.getAccessAction(), row.getTargetEntity(), ex.getMessage());
        }
    }

    private static void autofill(PrivacyAccessEvent e) {
        if (e.getActorUserId() == null
                || e.getActorUserType() == null
                || e.getActorLoginId() == null) {
            Authentication authn = SecurityContextHolder.getContext().getAuthentication();
            if (authn != null && authn.isAuthenticated()
                    && authn.getPrincipal() instanceof CustomUserDetails u) {
                if (e.getActorUserId() == null)   e.setActorUserId(u.getUserId());
                if (e.getActorUserType() == null) e.setActorUserType(u.getUserType());
                if (e.getActorLoginId() == null)  e.setActorLoginId(u.getUsername());
            }
        }
        if (e.getActorUserId() == null) {
            // HTTP 외 경로 (스케줄러 등) — SYSTEM 폴백
            e.setActorUserId(AuditContext.SYSTEM_USER);
        }
        if (e.getActorUserType() == null) {
            e.setActorUserType("SYSTEM");
        }
        HttpServletRequest req = currentRequest();
        if (req != null) {
            if (e.getHttpMethod() == null)  e.setHttpMethod(req.getMethod());
            if (e.getRequestUri() == null)  e.setRequestUri(req.getRequestURI());
            if (e.getClientIp() == null)    e.setClientIp(IpUtils.clientIp(req));
            if (e.getUserAgent() == null)   e.setUserAgent(req.getHeader("User-Agent"));
            if (e.getSessionId() == null && req.getSession(false) != null) {
                e.setSessionId(req.getSession(false).getId());
            }
        } else if (e.getClientIp() == null) {
            e.setClientIp(AuditContext.ip());
        }
        if (e.getTraceId() == null) {
            String tid = MDC.get("traceId");
            if (tid != null && !tid.isBlank()) e.setTraceId(tid);
        }
        if (e.getTargetCount() == null) {
            e.setTargetCount(1);
        }
        if (e.getResult() == null) {
            e.setResult(PrivacyAccessLog.RESULT_SUCCESS);
        }
    }

    private static PrivacyAccessLog toRow(PrivacyAccessEvent e) {
        PrivacyAccessLog row = new PrivacyAccessLog();
        row.setActorUserId(e.getActorUserId());
        row.setActorUserType(e.getActorUserType());
        row.setActorLoginId(e.getActorLoginId());
        row.setClientIp(e.getClientIp());
        row.setUserAgent(trim(e.getUserAgent(), 500));
        row.setSessionId(trim(e.getSessionId(), 100));
        row.setRequestUri(trim(e.getRequestUri(), 1000));
        row.setHttpMethod(e.getHttpMethod());
        row.setTraceId(e.getTraceId());
        row.setTargetEntity(e.getTargetEntity());
        row.setTargetUserType(e.getTargetUserType());
        row.setTargetId(e.getTargetId());
        row.setTargetCount(e.getTargetCount());
        row.setPiiFields(trim(e.getPiiFields(), 500));
        row.setAccessAction(e.getAccessAction());
        row.setAccessReason(trim(e.getAccessReason(), 2000));
        row.setResult(e.getResult());
        row.setFailReason(trim(e.getFailReason(), 500));
        LocalDateTime now = LocalDateTime.now();
        row.setAccessedAt(now);
        row.setCreatedBy(e.getActorUserId());
        row.setCreatedIp(e.getClientIp());
        row.setCreatedAt(now);
        return row;
    }

    private static String trim(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (ClassCastException | IllegalStateException ex) {
            return null;
        }
    }
}
