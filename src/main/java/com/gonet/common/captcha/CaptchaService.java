package com.gonet.common.captcha;

/**
 * CAPTCHA 검증 인터페이스 — provider 추상화.
 *
 * <p>호출 패턴 — 폼 POST 핸들러에서:
 * <pre>{@code
 * if (captchaService.isEnabled()) {
 *     CaptchaResult r = captchaService.verify(form.getCaptchaToken(), IpUtils.clientIp(req));
 *     if (!r.isSuccess()) {
 *         ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
 *         return "redirect:/...";
 *     }
 * }
 * }</pre>
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link NoOpCaptchaService} — enabled=false 시 자동 활성화. 항상 success.</li>
 *   <li>{@link GoogleRecaptchaV3Service} — Google reCAPTCHA v3 score-based.</li>
 *   <li>(향후) NaverCaptchaService — NCP 챌린지형.</li>
 * </ul>
 */
public interface CaptchaService {

    /**
     * 전역 활성화 여부. false 면 호출자는 verify 를 호출하지 않아도 됨.
     * (NoOp 도 isEnabled()=false 반환하므로, 호출자는 이 값으로 폼에 CAPTCHA 위젯 렌더 여부도 결정)
     */
    boolean isEnabled();

    /**
     * 클라이언트 site-key — Thymeleaf fragment 가 데이터-속성으로 노출.
     * 비활성 모드에서는 빈 문자열 반환.
     */
    String siteKey();

    /**
     * 클라이언트가 발급받은 token 을 provider 측에 위탁 검증.
     *
     * @param token 클라이언트 측 grecaptcha.execute() 결과 또는 등가
     * @param clientIp 사용자 실제 IP (Google v3 의 selenium-detection 등에 사용)
     * @return 검증 결과. provider 호출 실패 시에도 예외 던지지 않고 fail 반환 — UX 차단 회피
     */
    CaptchaResult verify(String token, String clientIp);
}
