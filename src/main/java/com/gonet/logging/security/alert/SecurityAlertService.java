package com.gonet.logging.security.alert;

import com.gonet.logging.security.dto.SecurityLog;

/**
 * 보안 이벤트 외부 알림 단일 진입점 — log_security 적재 직후 severity 기반 전송.
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link TelegramSecurityAlertService} — {@code gopcms.alert.telegram.enabled=true} 시 활성</li>
 *   <li>{@link NoOpSecurityAlertService} — 기본 폴백 ({@code @ConditionalOnMissingBean})</li>
 * </ul>
 *
 * <p>호출은 {@code SecurityLoggerImpl.writeAsync} 가 mapper.insert 직후 1회. 알림 실패는
 * 본 흐름을 막지 않으며 fallback logger 가 ERROR 라인으로 흡수.
 */
public interface SecurityAlertService {

    /**
     * 적재된 보안 이벤트가 알림 정책 (min-severity 등) 에 부합하면 외부로 전송.
     * 알림 비활성/severity 미달 시 silent skip.
     */
    void notifyIfApplicable(SecurityLog row);
}
