package com.gonet.common.captcha;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * CAPTCHA 검증 결과 — provider 중립 DTO.
 *
 * <p>{@link #success} 가 핵심. {@link #score} 는 Google v3 같은 score-based provider 만 채움
 * (Naver 등 챌린지형은 -1 로 반환). {@link #providerErrorCode} 는 디버깅용.
 */
@Getter
@RequiredArgsConstructor
public class CaptchaResult {

    private final boolean success;

    /** 0.0(봇) ~ 1.0(인간). 챌린지형 provider 는 -1. */
    private final double score;

    /** provider 가 반환한 원시 에러 코드. 성공 시 null. */
    private final String providerErrorCode;

    public static CaptchaResult success(double score) {
        return new CaptchaResult(true, score, null);
    }

    public static CaptchaResult fail(String errorCode) {
        return new CaptchaResult(false, 0.0, errorCode);
    }

    public static CaptchaResult fail(String errorCode, double score) {
        return new CaptchaResult(false, score, errorCode);
    }
}
