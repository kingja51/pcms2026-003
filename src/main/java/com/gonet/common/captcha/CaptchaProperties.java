package com.gonet.common.captcha;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CAPTCHA 설정 — {@code gopcms.captcha.*}.
 *
 * <p>provider 추상화 — 현재 GOOGLE_RECAPTCHA_V3 만 구현. 향후 NAVER_CAPTCHA 등 추가 시
 * {@link CaptchaService} 구현체를 새로 등록하고 {@code provider} 만 전환하면 됨.
 *
 * <p>{@code enabled=false} 시 {@link NoOpCaptchaService} 가 활성화되어 항상 success 반환.
 * 로컬 개발 / 단위 테스트 / CAPTCHA 미적용 운영 환경에서 사용.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.captcha")
public class CaptchaProperties {

    /** CAPTCHA 전역 사용 여부. false 면 NoOp 활성화. */
    private boolean enabled = false;

    /** Provider 식별자. 현재 지원: GOOGLE_RECAPTCHA_V3. */
    private Provider provider = Provider.GOOGLE_RECAPTCHA_V3;

    /** 클라이언트 측 site-key (Thymeleaf fragment 에 노출). */
    private String siteKey = "";

    /** 서버 측 secret-key (verify API 호출 시 사용). 절대 클라이언트 노출 금지. */
    private String secretKey = "";

    /**
     * v3 score 임계값. 0.0(봇 확실) ~ 1.0(인간 확실).
     * Google 권장 기본 0.5. 트래픽 분석 후 0.3~0.7 사이 조정.
     */
    private double scoreThreshold = 0.5;

    /** verify API base URL. 기본값 = Google 공식. */
    private String verifyUrl = "https://www.google.com/recaptcha/api/siteverify";

    /** RestClient connect timeout (ms). */
    private int connectTimeoutMs = 3000;

    /** RestClient read timeout (ms). */
    private int readTimeoutMs = 5000;

    public enum Provider {
        GOOGLE_RECAPTCHA_V3,
        NAVER_CAPTCHA  // 향후 추가 — 현재는 GOOGLE 만 작동
    }
}
