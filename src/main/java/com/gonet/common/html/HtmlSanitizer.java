package com.gonet.common.html;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

/**
 * 사용자 출력 HTML 의 표준 sanitize 진입점.
 *
 * <p>관리자가 입력한 raw HTML 도 화면 출력 전 본 컴포넌트를 거쳐야 한다.
 * 출력 정책은 운영 도메인별로 분기 — 팝업/배너의 본문은 광고/이미지/링크가 흔하므로
 * formatting + links + images + tables 정책 묶음 적용.
 *
 * <p>script/style/iframe 등 위험 태그는 정책 미허용으로 자동 제거.
 *
 * <p>OWASP Java HTML Sanitizer 가 의존성으로 이미 포함되어 있어 추가 라이브러리 불필요.
 */
@Component
public class HtmlSanitizer {

    /**
     * 팝업/배너 본문용 — 글머리/굵기/링크/이미지/표 허용. script/style/iframe 차단.
     * 추가로 {@code rel="noopener noreferrer"} 강제 + target="_blank" 만 허용.
     */
    private static final PolicyFactory CONTENT_POLICY =
        Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.TABLES)
            .and(new HtmlPolicyBuilder()
                .allowElements("span", "div", "br", "hr")
                .allowAttributes("class").globally()
                .allowAttributes("target").matching(true, "_self", "_blank")
                  .onElements("a")
                .toFactory());

    /** 모든 태그 차단 = 텍스트만 통과 (검색 색인 strip 과 동일). */
    private static final PolicyFactory STRIP_ALL_POLICY = new HtmlPolicyBuilder().toFactory();

    /**
     * 팝업/배너 본문 sanitize. null/빈 문자열은 그대로 반환.
     * 출력은 입력보다 짧거나 같음.
     */
    public String sanitizeContent(String html) {
        if (html == null || html.isBlank()) return html;
        return CONTENT_POLICY.sanitize(html);
    }

    /** 모든 태그 제거 — 텍스트만 추출 (검색용). */
    public String stripAll(String html) {
        if (html == null || html.isBlank()) return html;
        return STRIP_ALL_POLICY.sanitize(html);
    }
}
