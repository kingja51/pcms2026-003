package com.gonet.common.captcha;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 Controller 응답에 {@code captchaEnabled} / {@code captchaSiteKey} 자동 주입.
 *
 * <p>Thymeleaf fragment {@code fragments/captcha-v3 :: widget(action, formId)} 가 두 값으로
 * 렌더 여부와 site-key 를 결정. Controller 가 매번 model.addAttribute 호출할 필요 없음.
 *
 * <p>주입 비용 — boolean + String 2개. 매 요청 ms 단위 부하 무시 가능.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class CaptchaModelAdvice {

    private final CaptchaService captchaService;

    @ModelAttribute("captchaEnabled")
    public boolean captchaEnabled() {
        return captchaService.isEnabled();
    }

    @ModelAttribute("captchaSiteKey")
    public String captchaSiteKey() {
        return captchaService.siteKey();
    }
}
