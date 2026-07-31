package com.gonet.logging.security.alert;

import com.gonet.logging.security.dto.SecurityLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 폴백 — 알림 빈이 없을 때 ({@code gopcms.alert.telegram.enabled=false}) 사용.
 *
 * <p>호출자는 항상 비-null 빈을 받아 {@code if (alertService != null)} 가드 불필요.
 */
@Component
@ConditionalOnMissingBean(name = "telegramSecurityAlertService")
public class NoOpSecurityAlertService implements SecurityAlertService {

    @Override
    public void notifyIfApplicable(SecurityLog row) {
        // no-op
    }
}
