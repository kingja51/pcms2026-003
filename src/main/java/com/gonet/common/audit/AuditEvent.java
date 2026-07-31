package com.gonet.common.audit;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 감사 이벤트 1건 — 5경로 다중 기록(Multi-destination audit) 의 소스 데이터.
 *
 * <p>경로:
 * <ol>
 *   <li>{@code log_audit} 테이블 (Primary 경로 — logging DB)</li>
 *   <li>Logback JSON appender → file (Loki/ELK 수집)</li>
 *   <li>Spring {@link org.springframework.context.ApplicationEvent} — 내부 리스너(OTel/Kafka)</li>
 *   <li>MariaDB server_audit plugin (DBA 구성, 프로세스 외부)</li>
 *   <li>MariaDB binlog (DBA 구성, CDC 소스)</li>
 * </ol>
 *
 * <p>호출부 권장 패턴은 fluent {@code withXxx} 체이닝이며,
 * Lombok {@code @Setter} 가 생성하는 void setter 는 {@link AuditLogger#autofill}
 * 의 누락 필드 보강 전용이다 (호출부는 setter 직접 호출 지양).
 */
@Getter
@Setter
public class AuditEvent {

    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;
    private String action;            // CREATE/UPDATE/DELETE/LOGIN/ACCESS
    private String targetEntity;      // tb_admin / tb_role ...
    private String targetId;
    private String requestUri;
    private String httpMethod;
    private String clientIp;
    private String userAgent;
    private String beforeJson;
    private String afterJson;
    private String result;            // SUCCESS/FAIL/ERROR
    private String traceId;
    private LocalDateTime loggedAt;

    public static AuditEvent of(String action, String targetEntity) {
        AuditEvent e = new AuditEvent();
        e.action = action;
        e.targetEntity = targetEntity;
        e.result = "SUCCESS";
        e.loggedAt = LocalDateTime.now();
        return e;
    }

    public AuditEvent withActor(String userId, String userType, String loginId) {
        this.actorUserId = userId;
        this.actorUserType = userType;
        this.actorLoginId = loginId;
        return this;
    }

    public AuditEvent withHttp(String method, String uri, String ip, String userAgent) {
        this.httpMethod = method;
        this.requestUri = uri;
        this.clientIp = ip;
        this.userAgent = userAgent;
        return this;
    }

    public AuditEvent withTarget(String id) {
        this.targetId = id;
        return this;
    }

    public AuditEvent withResult(String result) {
        this.result = result;
        return this;
    }

    public AuditEvent withBefore(String json) {
        this.beforeJson = json;
        return this;
    }

    public AuditEvent withAfter(String json) {
        this.afterJson = json;
        return this;
    }

    public AuditEvent withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
