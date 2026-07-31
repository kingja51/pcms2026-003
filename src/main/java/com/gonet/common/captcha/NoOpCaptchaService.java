package com.gonet.common.captcha;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * CAPTCHA 비활성 폴백.
 *
 * <p>활성화 조건 — {@code gopcms.captcha.enabled=false} (기본값) 또는 미설정.
 * {@link GoogleRecaptchaV3Service} 와 정확히 배타적인 {@code @ConditionalOnProperty}
 * 조건을 가져 동시에 등록될 일이 없다.
 *
 * <p>주의 — 과거 {@code @ConditionalOnMissingBean(CaptchaService.class)} 도 함께 두었으나
 * Spring 의 빈 등록 순서 평가에서 false 로 떨어져 {@code enabled=false} 일 때 NoOp 도
 * 등록되지 않는 부팅 실패 버그가 있었다. {@code @ConditionalOnProperty} 단일 조건만 신뢰.
 *
 * <p>{@link #verify(String, String)} 는 항상 success 반환. 폼 위젯은 {@link #isEnabled()}
 * 가 false 면 렌더하지 않으므로 token 도 비어 있는 게 정상.
 */
@Component
@ConditionalOnProperty(prefix = "gopcms.captcha", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpCaptchaService implements CaptchaService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String siteKey() {
        return "";
    }

    @Override
    public CaptchaResult verify(String token, String clientIp) {
        return CaptchaResult.success(1.0);
    }
}
