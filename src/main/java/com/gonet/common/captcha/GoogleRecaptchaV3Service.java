package com.gonet.common.captcha;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Google reCAPTCHA v3 score-based 검증.
 *
 * <p>흐름:
 * <pre>
 *   1. [브라우저] grecaptcha.execute(SITE_KEY, {action:'login'}) → token
 *   2. [서버]   POST https://www.google.com/recaptcha/api/siteverify
 *             body: secret=&lt;SECRET&gt;&response=&lt;token&gt;&remoteip=&lt;ip&gt;
 *             resp: {success:true, score:0.9, action:'login', challenge_ts:..., hostname:...}
 *   3. score &gt;= threshold (default 0.5) 시 통과
 * </pre>
 *
 * <p>활성 조건 — {@code gopcms.captcha.enabled=true} + {@code provider=GOOGLE_RECAPTCHA_V3}.
 * 두 조건 모두 충족하지 않으면 {@link NoOpCaptchaService} 가 활성화.
 *
 * <p>네트워크 장애 / Google 응답 형식 변경 등 어떤 예외도 본 메서드 안에서 catch 하여
 * {@link CaptchaResult#fail(String)} 반환 — provider 장애가 사용자 로그인/가입을 막지 않는다.
 * 운영자는 {@code SECURITY_REJECT} 로그로 인지하고 임시로 enabled=false 전환 가능.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gopcms.captcha", name = "enabled", havingValue = "true")
public class GoogleRecaptchaV3Service implements CaptchaService {

    private final CaptchaProperties props;

    @Qualifier("captchaRestClient")
    private final RestClient captchaRestClient;

    @Override
    public boolean isEnabled() {
        return props.isEnabled()
            && props.getProvider() == CaptchaProperties.Provider.GOOGLE_RECAPTCHA_V3
            && StringUtils.hasText(props.getSecretKey())
            && StringUtils.hasText(props.getSiteKey());
    }

    @Override
    public String siteKey() {
        return props.getSiteKey();
    }

    @Override
    public CaptchaResult verify(String token, String clientIp) {
        if (!isEnabled()) {
            return CaptchaResult.success(1.0);
        }
        if (!StringUtils.hasText(token)) {
            log.warn("CAPTCHA_VERIFY fail reason=empty_token ip={}", clientIp);
            return CaptchaResult.fail("missing-input-response");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", props.getSecretKey());
        body.add("response", token);
        if (StringUtils.hasText(clientIp)) {
            body.add("remoteip", clientIp);
        }

        try {
            Map<?, ?> resp = captchaRestClient.post()
                .uri(props.getVerifyUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(Map.class);

            if (resp == null) {
                log.warn("CAPTCHA_VERIFY fail reason=null_response ip={}", clientIp);
                return CaptchaResult.fail("null-response");
            }

            boolean success = Boolean.TRUE.equals(resp.get("success"));
            double score = parseScore(resp.get("score"));
            Object errorCodes = resp.get("error-codes");
            String errorCode = errorCodes instanceof List<?> ec && !ec.isEmpty()
                ? String.valueOf(ec.get(0)) : null;

            if (!success) {
                log.warn("CAPTCHA_VERIFY fail reason=provider_rejected errors={} ip={}", errorCodes, clientIp);
                return CaptchaResult.fail(errorCode != null ? errorCode : "provider-rejected");
            }

            if (score < props.getScoreThreshold()) {
                log.warn("CAPTCHA_VERIFY fail reason=low_score score={} threshold={} ip={}",
                    score, props.getScoreThreshold(), clientIp);
                return CaptchaResult.fail("score-too-low", score);
            }

            log.info("===CAPTCHA_VERIFY ok score={} threshold={} ip={}", score, props.getScoreThreshold(), clientIp);
            return CaptchaResult.success(score);

        } catch (Exception ex) {
            log.warn("CAPTCHA_VERIFY error ip={} reason={}", clientIp, ex.getMessage());
            return CaptchaResult.fail("network-error");
        }
    }

    private double parseScore(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignore) { /* fall through */ }
        }
        return 0.0;
    }
}
