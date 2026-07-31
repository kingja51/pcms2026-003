package com.gonet.common.audit;

import com.gonet.common.util.IpUtils;
import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.log.dto.AuditLog;
import com.gonet.logging.log.mapper.AuditLogMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 감사 이벤트 5경로 팬아웃(fan-out) 엔트리포인트.
 *
 * <p>호출 1회 → 3경로 동기 + 2경로 외부(DBA):
 * <ol>
 *   <li>{@code log_audit} 테이블 INSERT (logging DB) — 이 클래스가 담당</li>
 *   <li>Logback JSON — 전용 logger {@code com.gonet.audit} 로 structured args 와 함께 기록</li>
 *   <li>Spring ApplicationEvent 발행 — 프로세스 내 리스너(OTel/Kafka 확장 지점)</li>
 *   <li>server_audit plugin — DBA 가 MariaDB 플러그인으로 구성 (프로세스 외부)</li>
 *   <li>binlog + CDC — DBA 가 row-based binlog 활성화 (Debezium 등 연동)</li>
 * </ol>
 *
 * <p>오류 내성: DB INSERT 실패는 WARN 로그로 남기고 감사 이벤트 유실 방지를 위해
 * JSON/Event 경로는 계속 시도한다. 감사 로그 유실은 장애로 간주되므로
 * Prometheus alert 를 향후 추가 예정 (S1 관측성 파트).
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final Logger audit = LoggerFactory.getLogger("com.gonet.audit");

    private final AuditLogMapper mapper;
    private final ApplicationEventPublisher publisher;
    /**
     * Self-proxy — @Transactional advisor 적용을 위해 같은 클래스 메서드를 호출할 때 사용.
     * Spring AOP 가 자기 호출(self-invocation) 에서는 advisor 를 적용하지 않으므로
     * 직접 {@code writeToDb()} 를 부르면 REQUIRES_NEW 가 무시된다. {@code @Lazy} 가
     * 순환 참조를 회피하면서 진짜 proxy 빈을 주입한다.
     */
    private final AuditLogger self;

    public AuditLogger(AuditLogMapper mapper,
                        ApplicationEventPublisher publisher,
                        @Lazy AuditLogger self) {
        this.mapper = mapper;
        this.publisher = publisher;
        this.self = self;
    }

    public void write(AuditEvent event) {
        if (event == null) return;
        // 호출부에서 .withActor() / .withHttp() / .withTraceId() 를 빠뜨려도
        // SecurityContext + RequestContextHolder + MDC 에서 자동 보강한다.
        // EXCEL_DOWNLOAD 등 14곳 Mng Controller 가 actor/http 미지정 호출하던 회귀 방어.
        autofill(event);
        // 1) DB — self-proxy 경유로 @Transactional(REQUIRES_NEW, logging tx) 강제 적용
        try {
            self.writeToDb(event);
        } catch (Exception ex) {
            log.warn("AUDIT db-write failed action={} entity={} reason={}",
                event.getAction(), event.getTargetEntity(), ex.getMessage());
        }
        // 2) Logback JSON
        writeToLog(event);
        // 3) Spring Event
        try {
            publisher.publishEvent(new AuditSpringEvent(this, event));
        } catch (Exception ex) {
            log.warn("AUDIT event-publish failed reason={}", ex.getMessage());
        }
    }

    private static void autofill(AuditEvent e) {
        // actor — SecurityContextHolder lazy 조회
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
        // HTTP — 현재 요청 스레드에서만 의미 있음 (스케줄러/비동기 스레드는 null)
        HttpServletRequest req = currentRequest();
        if (req != null) {
            if (e.getHttpMethod() == null)  e.setHttpMethod(req.getMethod());
            if (e.getRequestUri() == null)  e.setRequestUri(req.getRequestURI());
            if (e.getClientIp() == null)    e.setClientIp(IpUtils.clientIp(req));
            if (e.getUserAgent() == null)   e.setUserAgent(req.getHeader("User-Agent"));
        } else if (e.getClientIp() == null) {
            // 비-HTTP 스레드에서도 AuditContextFilter 가 ThreadLocal 에 IP 를 남겼다면 활용
            String ip = AuditContext.ip();
            if (!AuditContext.UNKNOWN_IP.equals(ip)) e.setClientIp(ip);
        }
        // traceId — Micrometer/MDC
        if (e.getTraceId() == null) {
            String tid = MDC.get("traceId");
            if (tid != null && !tid.isBlank()) e.setTraceId(tid);
        }
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

    /**
     * 감사 이벤트 DB INSERT — <b>항상 별도 트랜잭션</b>(REQUIRES_NEW + logging tx mgr).
     *
     * <p>호출자(primary 도메인 비즈니스 로직)가 rollback 되어도 감사 로그는 보존된다.
     * 또한 호출자가 logging 도메인 내부 흐름이라 logging TX 가 이미 활성이어도
     * 새 트랜잭션을 강제로 열어 fan-out 의 독립성을 보장.
     *
     * <p>Spring AOP 가 자기 호출(self-invocation) 에서는 advisor 를 적용하지 않으므로
     * 본 메서드는 반드시 <b>같은 클래스의 다른 빈 경로</b>가 아닌 외부({@link #write})에서만
     * 호출한다 — write() 가 public 진입점, writeToDb 는 사실상 package-private 보조.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
                   transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
    void writeToDb(AuditEvent e) {
        AuditLog row = new AuditLog();
        row.setActorUserId(e.getActorUserId());
        row.setActorUserType(e.getActorUserType());
        row.setActorLoginId(e.getActorLoginId());
        row.setAction(e.getAction());
        row.setTargetEntity(e.getTargetEntity());
        row.setTargetId(e.getTargetId());
        row.setRequestUri(trim(e.getRequestUri(), 1000));
        row.setHttpMethod(e.getHttpMethod());
        row.setClientIp(e.getClientIp());
        row.setUserAgent(trim(e.getUserAgent(), 500));
        row.setBeforeJson(e.getBeforeJson());
        row.setAfterJson(e.getAfterJson());
        row.setResult(e.getResult());
        row.setTraceId(e.getTraceId());
        row.setLoggedAt(e.getLoggedAt() == null ? LocalDateTime.now() : e.getLoggedAt());

        String actor = e.getActorUserId() == null ? AuditContext.SYSTEM_USER : e.getActorUserId();
        String ip    = e.getClientIp() == null ? AuditContext.UNKNOWN_IP : e.getClientIp();
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedBy(actor);
        row.setCreatedIp(ip);
        row.setCreatedAt(now);
        row.setUpdatedBy(actor);
        row.setUpdatedIp(ip);
        row.setUpdatedAt(now);

        mapper.insert(row);
    }

    private void writeToLog(AuditEvent e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("actorUserId",   e.getActorUserId());
        fields.put("actorUserType", e.getActorUserType());
        fields.put("actorLoginId",  e.getActorLoginId());
        fields.put("action",        e.getAction());
        fields.put("targetEntity",  e.getTargetEntity());
        fields.put("targetId",      e.getTargetId());
        fields.put("requestUri",    e.getRequestUri());
        fields.put("httpMethod",    e.getHttpMethod());
        fields.put("clientIp",      e.getClientIp());
        fields.put("result",        e.getResult());
        fields.put("traceId",       e.getTraceId());
        audit.info("AUDIT {} {} {}",
            nvl(e.getAction()), nvl(e.getTargetEntity()), nvl(e.getTargetId()),
            StructuredArguments.entries(fields));
    }

    private static String trim(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    private static String nvl(String s) { return s == null ? "-" : s; }
}
