package com.gonet.logging.security.dto;

/**
 * log_security.severity 컬럼 — DB CHECK 제약과 일치 (INFO|WARN|CRITICAL).
 *
 * <p>운영 알림 정책 매핑 (가이드):
 * <ul>
 *   <li>{@link #INFO}     — 집계만, 알림 없음. (예: RATE_LIMIT_BLOCK)</li>
 *   <li>{@link #WARN}     — 일별 다이제스트 + threshold 초과 시 알림. (예: LOGIN_LOCK, IP_DENY, UPLOAD_BLOCK)</li>
 *   <li>{@link #CRITICAL} — 즉시 알림 (Slack/PagerDuty). (예: VIRUS_SCAN_INFECTED, PRIVILEGE_ESCALATION)</li>
 * </ul>
 */
public enum SecurityEventSeverity {
    INFO,
    WARN,
    CRITICAL
}
