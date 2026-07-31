package com.gonet.logging.security.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 보안 이벤트 — {@link com.gonet.logging.security.service.SecurityLogger} 입력.
 *
 * <p>호출 패턴 ({@link com.gonet.common.audit.AuditEvent} 와 동일):
 * <pre>
 * securityLogger.write(
 *   SecurityEvent.of(SecurityEventType.UPLOAD_BLOCK)
 *     .withDetail("{\"filename\":\"" + JsonUtils.escape(name) + "\",\"reason\":\"DOUBLE_EXTENSION\"}")
 * );
 * </pre>
 *
 * <p>actor / IP / HTTP 정보는 호출자가 비워두면 SecurityLogger 가 호출자 스레드에서
 * SecurityContext + RequestContextHolder 로 자동 보강 (PrivacyAccessLogger 와 동일 패턴).
 */
@Getter
@Setter
public class SecurityEvent {

    private SecurityEventType     type;
    private SecurityEventSeverity severity;

    // Who — 미세팅 시 자동 보강
    private String actorId;

    // Where — 미세팅 시 자동 보강
    private String clientIp;
    private String userAgent;
    private String requestUri;
    /** scheme://host:port (autofill 자동 채움). 텔레그램 등 알림에서 풀 URL 표시용. DB 미저장. */
    private String requestHost;
    private String traceId;

    // 이벤트별 상세 — JSON 문자열, json_valid 통과해야 함
    private String detailJson;

    public static SecurityEvent of(SecurityEventType type) {
        SecurityEvent e = new SecurityEvent();
        e.type = type;
        e.severity = type.defaultSeverity();
        return e;
    }

    public SecurityEvent withSeverity(SecurityEventSeverity severity) {
        this.severity = severity;
        return this;
    }

    public SecurityEvent withActor(String actorId) {
        this.actorId = actorId;
        return this;
    }

    public SecurityEvent withDetail(String detailJson) {
        this.detailJson = detailJson;
        return this;
    }

    public SecurityEvent withRequest(String method, String uri) {
        // method 는 별도 컬럼 없음 — detail_json 에 합치거나 무시
        this.requestUri = uri;
        return this;
    }
}
