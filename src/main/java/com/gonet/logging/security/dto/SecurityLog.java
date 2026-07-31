package com.gonet.logging.security.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * log_security INSERT 용 DTO (logging DB).
 *
 * <p>보안 이벤트 분리 적재 — 기존 log_audit 와 달리 SOC/SIEM 모니터링 일차 소스로 사용.
 * 운영 알림 정책 (slack/pagerduty) 은 {@link SecurityEventSeverity} 기준.
 *
 * <p>append-only 정책 — log_* 표준에 맞춰 created_* 만 보유 (updated_* 는 §0.48 DDL 로 DROP).
 */
@Getter
@Setter
public class SecurityLog {

    private Long logSecurityId;

    /** {@link SecurityEventType#name()} */
    private String eventType;

    /** {@link SecurityEventSeverity#name()} */
    private String severity;

    private String actorId;
    private String clientIp;
    private String userAgent;
    private String requestUri;

    /** 임의 구조 JSON — 이벤트별 상세 (filename, virusName, reason 등). DB CHECK json_valid 적용. */
    private String detailJson;

    private String        traceId;
    private LocalDateTime loggedAt;

    /**
     * 요청 호스트 — {@code scheme://host:port} (예: {@code http://127.0.0.1}).
     * <p>DB 컬럼 X — INSERT XML 에 미포함. 메모리 내 알림 메시지(텔레그램 등) 빌드 시
     * {@code requestHost + requestUri} 로 풀 URL 표시 용도. {@link com.gonet.logging.security.service.SecurityLoggerImpl#autofill}
     * 에서 {@link jakarta.servlet.http.HttpServletRequest#getRequestURL()} 결과 일부로 자동 채움.
     */
    private String        requestHost;

    // append-only 감사컬럼
    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
}
