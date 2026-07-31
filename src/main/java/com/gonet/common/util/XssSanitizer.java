package com.gonet.common.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * 사용자 입력 HTML 의 XSS 제거 유틸 — <b>화이트리스트 정책</b>.
 *
 * <p>본 유틸은 화면 출력 직전에도, DB 저장 직전에도 동일하게 호출한다 — 즉 게시판 마스터의
 * {@code html_yn='Y'} 라 하더라도 본 sanitizer 를 반드시 거친다 (defense-in-depth).
 *
 * <p>허용 정책:
 * <ul>
 *   <li>인라인 서식: {@code b strong i em u s strike sub sup mark small code}</li>
 *   <li>블록: {@code p br hr div span pre blockquote h1~h6 ul ol li dl dt dd}</li>
 *   <li>표: {@code table thead tbody tfoot tr th td caption} + {@code colspan rowspan}</li>
 *   <li>링크: {@code <a>} — {@code href}({@code http/https/mailto/tel/#fragment}) + {@code target="_blank"} 만 허용. {@code rel="noopener noreferrer"} 자동 부여 (취약점 차단)</li>
 *   <li>이미지: {@code <img>} — {@code src}({@code http/https/data:image/}) + {@code alt width height}</li>
 *   <li>공통 속성: {@code class title}</li>
 * </ul>
 *
 * <p><b>차단 (정책 미허용 = 자동 제거)</b>:
 * <ul>
 *   <li>{@code <script>} / {@code <style>} / {@code <iframe>} / {@code <object>} / {@code <embed>} /
 *       {@code <link>} / {@code <meta>} / {@code <base>} / {@code <form>} / {@code <input>} / {@code <button>} 등 모든 위험 태그</li>
 *   <li>모든 이벤트 핸들러 속성 — {@code onclick onerror onload onmouseover ...}</li>
 *   <li>{@code style} 속성 자체 — CSS expression / behavior 우회 차단</li>
 *   <li>{@code javascript:} / {@code vbscript:} / {@code data:text/html} URL — href/src 정책에서 거부</li>
 * </ul>
 *
 * <p>Thymeleaf 에서의 사용: {@code th:utext="${@xss.safe(article.content)}"}.
 * 빈 이름이 {@code xss} 라 짧게 호출 가능.
 *
 * @see <a href="https://owasp.org/www-project-java-html-sanitizer/">OWASP Java HTML Sanitizer</a>
 */
@Component("xss")
public class XssSanitizer {

    /**
     * 화이트리스트 정책 — 본 클래스의 핵심 데이터.
     *
     * <p>의도적으로 {@code org.owasp.html.Sanitizers} 의 미리 정의된 set 을 사용하지 않고
     * {@link HtmlPolicyBuilder} 로 직접 구성. {@code Sanitizers.STYLES} 는 {@code style}
     * 속성을 허용하는데, 본 정책은 이를 거부해야 하기 때문.
     */
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
        // ── 인라인 서식 ──────────────────────────────────────────────────
        .allowElements("b", "strong", "i", "em", "u", "s", "strike",
                       "sub", "sup", "mark", "small", "code")

        // ── 블록 ────────────────────────────────────────────────────────
        .allowElements("p", "br", "hr", "div", "span", "pre", "blockquote",
                       "h1", "h2", "h3", "h4", "h5", "h6",
                       "ul", "ol", "li", "dl", "dt", "dd")

        // ── 표 ─────────────────────────────────────────────────────────
        .allowElements("table", "thead", "tbody", "tfoot",
                       "tr", "th", "td", "caption")
        .allowAttributes("colspan", "rowspan").onElements("th", "td")

        // ── 공통 속성 (class / title) ────────────────────────────────────
        .allowAttributes("class").globally()
        .allowAttributes("title").globally()

        // ── 링크 ────────────────────────────────────────────────────────
        .allowElements("a")
        .allowAttributes("href")
            .matching(true, "^(?:https?:|mailto:|tel:|#).*").onElements("a")
        .allowAttributes("target")
            .matching(true, "_self", "_blank").onElements("a")
        .requireRelNofollowOnLinks()    // rel="nofollow" 자동 부여 (스팸/SEO 보호)

        // ── 이미지 ──────────────────────────────────────────────────────
        .allowElements("img")
        .allowAttributes("src")
            .matching(true, "^(?:https?:|data:image/(?:png|jpe?g|gif|webp);base64,).*")
            .onElements("img")
        .allowAttributes("alt", "width", "height").onElements("img")

        .toFactory();

    /**
     * HTML 입력을 화이트리스트 정책으로 정제. 정책 외 모든 태그/속성은 자동 제거.
     *
     * <p>null/빈 문자열은 그대로 반환. 출력은 입력보다 짧거나 같음.
     *
     * <p>Thymeleaf 예: {@code th:utext="${@xss.safe(article.content)}"}.
     */
    public String safe(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return POLICY.sanitize(html);
    }

    /**
     * 모든 태그를 제거한 평문만 반환 — 검색 색인용/snippet 추출용.
     * {@code th:text} 사용 시에는 호출 불필요 (Thymeleaf 가 자동 이스케이프).
     */
    public String stripAll(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return new HtmlPolicyBuilder().toFactory().sanitize(html);
    }
}
