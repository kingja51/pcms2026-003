package com.gonet.logging.security.dto;

/**
 * 보안 이벤트 유형 카탈로그 — log_security.event_type 컬럼 표준화.
 *
 * <p>본 enum 의 name() 이 그대로 DB 에 저장된다 (대문자 SNAKE_CASE).
 * SOC/SIEM 알림 룰은 본 카탈로그 키를 기준으로 운영.
 *
 * <p>1단계 적재 (§0.48):
 * <ul>
 *   <li>{@link #UPLOAD_BLOCK} — 6중 방어 스택의 1·2·3·4 단계 차단</li>
 *   <li>{@link #LOGIN_LOCK} — 5회 실패 자동 잠금 발동</li>
 *   <li>{@link #IP_DENY} — 관리자 로그인 IP 화이트리스트 위반 (404 은닉 직전)</li>
 *   <li>{@link #VIRUS_SCAN_INFECTED} — ClamAV 감염 탐지 (CRITICAL)</li>
 * </ul>
 *
 * <p>후속 적재 예정:
 * <ul>
 *   <li>{@link #RATE_LIMIT_BLOCK} — Caffeine RateLimit 초과</li>
 *   <li>{@link #SSRF_BLOCK} — HttpFirewall / SuspiciousRequestFilter 차단</li>
 *   <li>{@link #TWO_FACTOR_FAIL} — TOTP 검증 실패</li>
 *   <li>{@link #PRIVILEGE_ESCALATION} — DynamicAuthorizationManager 거부 시</li>
 * </ul>
 */
public enum SecurityEventType {

    // ---- 1단계 적재 ----
    UPLOAD_BLOCK,
    LOGIN_LOCK,
    IP_DENY,
    VIRUS_SCAN_INFECTED,

    // ---- 후속 (enum 정의는 미리 두고 적재만 후속 PR) ----
    RATE_LIMIT_BLOCK,
    SSRF_BLOCK,
    TWO_FACTOR_FAIL,
    PRIVILEGE_ESCALATION;

    /** 기본 severity 추천값 — 호출자가 override 할 수 있음. */
    public SecurityEventSeverity defaultSeverity() {
        return switch (this) {
            case VIRUS_SCAN_INFECTED, PRIVILEGE_ESCALATION -> SecurityEventSeverity.CRITICAL;
            case RATE_LIMIT_BLOCK                          -> SecurityEventSeverity.INFO;
            default                                        -> SecurityEventSeverity.WARN;
        };
    }
}
