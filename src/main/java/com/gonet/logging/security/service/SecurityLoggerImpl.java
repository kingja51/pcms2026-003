package com.gonet.logging.security.service;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.util.IpUtils;
import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.security.alert.SecurityAlertService;
import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityEventSeverity;
import com.gonet.logging.security.dto.SecurityLog;
import com.gonet.logging.security.mapper.SecurityLogMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
 * {@link SecurityLogger} 구현 — log_security INSERT.
 *
 * <p>설계 포인트 (PrivacyAccessLoggerImpl 와 동일 패턴):
 * <ul>
 *   <li>{@code @Async("accessLogExecutor")} — 응답 commit 직후 백그라운드 INSERT
 *       (별도 executor 추가 부담을 피해 access 로그 executor 재사용)</li>
 *   <li>{@code REQUIRES_NEW} + {@code loggingTransactionManager} — 호출자 TX 와 분리
 *       (보안 이벤트는 호출자 롤백과 무관하게 적재되어야 함)</li>
 *   <li>actor / IP / HTTP 정보는 호출자 스레드에서 미리 보강 후 DTO 에 박아 비동기로 넘김
 *       — 워커 스레드는 SecurityContext 가 비어 있을 수 있음 (AccessLoggerImpl 동일 회귀)</li>
 * </ul>
 *
 * <p>적재 실패는 fallback SLF4J 로거({@code com.gonet.security.fallback}) 로 ERROR 라인 기록 —
 * SIEM 직접 수집용 (§0.46 L3 패턴 재사용). 적재 실패가 사용자 응답을 막아선 안 된다.
 */
@Service
public class SecurityLoggerImpl extends EgovAbstractServiceImpl implements SecurityLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityLoggerImpl.class);
    /** SIEM 직접 수집용 fallback 로거 — RollingFileAppender 분리 권장. */
    private static final Logger fallback = LoggerFactory.getLogger("com.gonet.security.fallback");

    private final SecurityLogMapper      mapper;
    private final SecurityAlertService   alertService;
    /**
     * §0.46 M3 패턴 — self-invocation 회피. {@code @Lazy} 로 순환 주입 회피.
     * Spring 환경에서는 SecurityLogger 의 AOP 프록시 빈이 주입되어
     * {@link #write(SecurityEvent)} 가 {@code self.writeAsync(row)} 호출 시
     * AsyncInterceptor + TransactionInterceptor 가 정상 적용된다.
     *
     * <p>단위 테스트(Spring 컨텍스트 비의존)에서는 보조 생성자로 self=null 주입.
     * write() 가 null 체크 후 직접 writeAsync() 호출 — 동기 실행되어 mock mapper 검증 가능.
     */
    private final SecurityLogger         self;

    @Autowired
    public SecurityLoggerImpl(SecurityLogMapper mapper,
                               SecurityAlertService alertService,
                               @Lazy SecurityLogger self) {
        this.mapper       = mapper;
        this.alertService = alertService;
        this.self         = self;
    }

    /** 단위 테스트용 보조 생성자 — Spring 프록시 없이 직접 인스턴스화 (Spring 은 @Autowired 가 명시된 위 생성자 사용). */
    public SecurityLoggerImpl(SecurityLogMapper mapper,
                               SecurityAlertService alertService) {
        this(mapper, alertService, null);
    }

    @Override
    public void write(SecurityEvent event) {
        if (event == null || event.getType() == null) return;
        // 호출자 스레드에서 actor/HTTP 보강 — 비동기 워커는 SecurityContext 비어있을 수 있음
        autofill(event);
        SecurityLog row = toRow(event);
        // self 가 있으면 프록시 호출 → @Async + @Transactional(REQUIRES_NEW) 정상 적용
        // 없으면 (단위 테스트) 직접 호출 — 동기/Tx 없이 mock mapper 검증
        if (self != null) {
            self.writeAsync(row);
        } else {
            writeAsync(row);
        }
    }

    @Override
    @Async("accessLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW,
                   transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
    public void writeAsync(SecurityLog row) {
        try {
            mapper.insert(row);
        } catch (Exception ex) {
            // 1차 fallback — DB 적재 실패 시 stdout/RollingFileAppender 로 SIEM 직접 수집
            fallback.error("SECURITY_LOG_FAIL event_type={} severity={} actor={} ip={} detail={} reason={}",
                row.getEventType(), row.getSeverity(), row.getActorId(), row.getClientIp(),
                row.getDetailJson(), ex.getMessage());
            log.warn("SECURITY_LOG_INSERT_FAIL event_type={} reason={}",
                row.getEventType(), ex.getMessage());
        }
        // 외부 알림 (텔레그램 등) — DB 적재 성공/실패와 무관하게 시도. min-severity 미달 시 silent skip.
        // alertService 자체 try-catch 가 호출 실패를 fallback logger 로 흡수.
        try {
            alertService.notifyIfApplicable(row);
        } catch (Exception ex) {
            // alertService 의 NoOp 폴백/Telegram 구현 모두 내부에서 throw 안 하도록 작성됨.
            // 그래도 만에 하나 RuntimeException 누출 시 본 트랜잭션 차단을 막기 위해 흡수.
            log.warn("SECURITY_ALERT_DISPATCH_FAIL event_type={} reason={}",
                row.getEventType(), ex.getMessage());
        }
    }

    private static void autofill(SecurityEvent e) {
        if (e.getSeverity() == null) {
            e.setSeverity(e.getType().defaultSeverity());
        }
        if (e.getActorId() == null) {
            Authentication authn = SecurityContextHolder.getContext().getAuthentication();
            if (authn != null && authn.isAuthenticated()
                    && authn.getPrincipal() instanceof CustomUserDetails u) {
                e.setActorId(u.getUserId());
            } else {
                e.setActorId(AuditContext.userId()); // ANONYMOUS / SYSTEM 폴백
            }
        }
        HttpServletRequest req = currentRequest();
        if (req != null) {
            if (e.getRequestUri() == null)  e.setRequestUri(req.getRequestURI());
            if (e.getClientIp() == null)    e.setClientIp(IpUtils.clientIp(req));
            if (e.getUserAgent() == null)   e.setUserAgent(req.getHeader("User-Agent"));
            if (e.getRequestHost() == null) e.setRequestHost(buildHost(req));
        } else if (e.getClientIp() == null) {
            e.setClientIp(AuditContext.ip());
        }
        if (e.getTraceId() == null) {
            String tid = MDC.get("traceId");
            if (tid != null && !tid.isBlank()) e.setTraceId(tid);
        }
    }

    private static SecurityLog toRow(SecurityEvent e) {
        SecurityLog row = new SecurityLog();
        row.setEventType(e.getType().name());
        row.setSeverity((e.getSeverity() == null
            ? SecurityEventSeverity.WARN : e.getSeverity()).name());
        row.setActorId(e.getActorId());
        row.setClientIp(e.getClientIp());
        row.setUserAgent(trim(e.getUserAgent(), 500));
        row.setRequestUri(trim(e.getRequestUri(), 1000));
        row.setRequestHost(e.getRequestHost());   // DB 미저장 — 알림 메시지 빌드용
        row.setDetailJson(e.getDetailJson());
        row.setTraceId(e.getTraceId());
        LocalDateTime now = LocalDateTime.now();
        row.setLoggedAt(now);
        row.setCreatedBy(e.getActorId());
        row.setCreatedIp(e.getClientIp());
        row.setCreatedAt(now);
        return row;
    }

    private static String trim(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    /**
     * {@code req.getRequestURL()} 에서 path 를 제외한 prefix(scheme://host:port) 추출.
     * 예: {@code http://127.0.0.1:80/admin/login} → {@code http://127.0.0.1:80}.
     * X-Forwarded-Host/Proto 는 forward-headers-strategy=native 가 알아서 적용된 결과 반환.
     */
    private static String buildHost(HttpServletRequest req) {
        StringBuffer url = req.getRequestURL();
        if (url == null) return null;
        String full = url.toString();
        // scheme://host:port/ ... — '/' 3번째 등장 위치까지가 prefix
        int afterScheme = full.indexOf("://");
        if (afterScheme < 0) return null;
        int hostEnd = full.indexOf('/', afterScheme + 3);
        return (hostEnd < 0) ? full : full.substring(0, hostEnd);
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
