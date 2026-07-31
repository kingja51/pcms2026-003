package com.gonet.common.captcha;

import com.gonet.common.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Controller 측 CAPTCHA 검증 단일 진입점.
 *
 * <p>호출 패턴 (1줄):
 * <pre>{@code
 * if (!captchaGuard.verifyOrFail(req)) {
 *     ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
 *     return "redirect:" + failureUrl;
 * }
 * }</pre>
 *
 * <p>Provider 가 비활성(NoOp) 이면 항상 true — 호출자는 분기 부담 없음.
 *
 * <p>token 파라미터 이름은 Google reCAPTCHA 표준 {@code g-recaptcha-response} 고정.
 * 폼이 fragments/captcha-v3 widget 을 포함하면 자동 채워짐.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaGuard {

    /** Google reCAPTCHA 표준 hidden input 명. */
    public static final String TOKEN_PARAM = "g-recaptcha-response";

    private final CaptchaService captchaService;

    /**
     * 요청에서 token 추출 후 검증. provider 비활성이면 즉시 true.
     *
     * @return true = 통과(또는 비활성) / false = 검증 실패
     */
    public boolean verifyOrFail(HttpServletRequest req) {
        if (!captchaService.isEnabled()) {
            log.info("===CAPTCHA_GUARD skip reason=DISABLED uri={}", req.getRequestURI());
            return true;
        }
        String token = req.getParameter(TOKEN_PARAM);
        String ip = IpUtils.clientIp(req);
        String tokenInfo = token == null ? "null" : ("len=" + token.length());
        log.info("===CAPTCHA_GUARD verify start uri={} ip={} token={}", req.getRequestURI(), ip, tokenInfo);
        CaptchaResult r = captchaService.verify(token, ip);
        log.info("===CAPTCHA_GUARD verify result={} score={} errorCode={} uri={}",
            r.isSuccess() ? "PASS" : "FAIL", r.getScore(), r.getProviderErrorCode(), req.getRequestURI());
        return r.isSuccess();
    }

    /** 명시 token 으로 검증 (Spring Security filter 등에서 useful). */
    public boolean verifyOrFail(String token, String clientIp) {
        if (!captchaService.isEnabled()) return true;
        CaptchaResult r = captchaService.verify(token, clientIp);
        log.info("===CAPTCHA_GUARD verify(explicit) result={} score={} errorCode={} ip={}",
            r.isSuccess() ? "PASS" : "FAIL", r.getScore(), r.getProviderErrorCode(), clientIp);
        return r.isSuccess();
    }
}
