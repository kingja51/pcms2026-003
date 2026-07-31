package com.gonet.fragment;

import com.gonet.common.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code fragments/pagination} 렌더 검증.
 *
 * <p>이 조각은 003 신규 작성이라 이식 검증(001 과 대조)이 통하지 않는다. 그리고
 * 페이징은 <b>틀려도 화면이 죽지 않는</b> 종류의 결함이다 — 번호 범위가 어긋나거나
 * 검색조건이 링크에서 빠져도 페이지는 정상 200 으로 뜬다. 눈으로 잡기 어려우므로
 * 렌더 결과를 직접 검사한다.
 *
 * <p>DB·Spring 컨텍스트 없이 Thymeleaf 엔진만 띄운다. 링크식 {@code @{...}} 이
 * contextPath 를 붙이므로 서블릿 요청 객체는 필요하다.
 *
 * <p><b>검증 못 하는 것</b>: {@code pageQuery} 는 {@code SiteContextModelAdvice} 가
 * 실제 요청에서 만든다. 여기서는 값을 직접 주입해 조각이 그걸 <b>이어붙이는지</b>만 본다.
 * advice 자체의 인코딩·page 제거 동작은 {@link SiteContextModelAdviceTest} 가 본다.
 */
class PaginationFragmentTest {

    private static final String FRAGMENT = "fragments/pagination";

    /**
     * 조각을 화면에서 쓰는 형태 그대로 호출하는 <b>래퍼 템플릿</b>을 렌더한다.
     *
     * <p>{@code engine.process(name, selectors, ctx)} 로는 안 된다 — 두 번째 인자는
     * markup selector 라 {@code render(${page}, ...)} 를 <b>조각 인자가 아니라 선택자
     * 문법으로 파싱</b>해 실패한다. 인자를 넘기려면 {@code th:replace} 를 실제로 거쳐야 한다.
     *
     * <p>그래서 리졸버를 둘 둔다: 조각은 classpath 에서, 래퍼는 문자열에서 온다.
     * {@code resolvablePatterns} 로 구분하지 않으면 String 리졸버가 조각 이름까지 삼킨다.
     */
    private String render(PageResponse<String> page, String baseUrl, String pageQuery, String fragmentCall) {
        ClassLoaderTemplateResolver classpath = new ClassLoaderTemplateResolver();
        classpath.setPrefix("templates/");
        classpath.setSuffix(".html");
        classpath.setTemplateMode(TemplateMode.HTML);
        classpath.setCharacterEncoding("UTF-8");
        classpath.setResolvablePatterns(java.util.Set.of("fragments/*"));
        classpath.setOrder(1);

        StringTemplateResolver inline = new StringTemplateResolver();
        inline.setTemplateMode(TemplateMode.HTML);
        inline.setOrder(2);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(classpath);
        engine.addTemplateResolver(inline);

        MockHttpServletRequest req = new MockHttpServletRequest(new MockServletContext());
        WebContext ctx = new WebContext(
            JakartaServletWebApplication.buildApplication(req.getServletContext())
                .buildExchange(req, new MockHttpServletResponse()),
            java.util.Locale.KOREA,
            Map.of("page", page, "baseUrl", baseUrl, "pageQuery", pageQuery));

        String wrapper = "<div th:replace=\"~{" + FRAGMENT + " :: " + fragmentCall + "}\"></div>";
        return engine.process(wrapper, ctx);
    }

    /** 조각 호출은 화면에서 쓰는 형태 그대로 — 인자 전달까지 함께 검증된다. */
    private String renderDefault(PageResponse<String> page, String pageQuery) {
        return render(page, "/admin/system/board", pageQuery,
                      "render(${page}, ${baseUrl})");
    }

    private static PageResponse<String> pageOf(int current, int totalPages) {
        int size = 10;
        return PageResponse.of(List.of("row"), current, size, (long) totalPages * size);
    }

    @Test
    @DisplayName("전체 1쪽이면 아무것도 렌더하지 않는다 — 쓸모없는 네비게이션을 남기지 않는다")
    void singlePageRendersNothing() {
        String html = renderDefault(pageOf(1, 1), "");
        assertThat(html).doesNotContain("<nav");
    }

    @Test
    @DisplayName("현재 페이지는 링크가 아니라 aria-current=page 인 span 이다")
    void currentPageIsMarkedAndNotALink() {
        String html = renderDefault(pageOf(3, 5), "");

        assertThat(html).contains("aria-current=\"page\"");
        // 현재 페이지 3 은 span 안에 있고, 그 span 에 href 가 없다
        assertThat(html).containsPattern("(?s)<span[^>]*aria-current=\"page\"[^>]*>\\s*3\\s*</span>");
        // 자기 자신으로 가는 링크는 만들지 않는다
        assertThat(html).doesNotContain("?page=3&");
        assertThat(html).doesNotContain("?page=3\"");
    }

    @Test
    @DisplayName("검색조건(pageQuery)이 모든 페이지 링크에 그대로 붙는다 — 넘기면 검색이 풀리는 회귀 방지")
    void searchConditionsSurvivePaging() {
        String html = renderDefault(pageOf(2, 4), "&keyword=%EA%B3%B5%EC%A7%80&pageSize=20");

        // href 안의 & 는 &amp; 로 나온다 — HTML 이스케이프가 맞다(브라우저가 & 로 되돌린다).
        // 한글 keyword 는 pageQuery 단계에서 이미 percent-encoding 돼 있다.
        assertThat(html).contains("?page=1&amp;keyword=%EA%B3%B5%EC%A7%80&amp;pageSize=20");
        assertThat(html).contains("?page=3&amp;keyword=%EA%B3%B5%EC%A7%80&amp;pageSize=20");
        assertThat(html).contains("?page=4&amp;keyword=%EA%B3%B5%EC%A7%80&amp;pageSize=20");
        // 검색조건 없는 링크가 하나라도 남으면 그 버튼만 검색이 풀린다
        assertThat(html).doesNotContain("?page=1\"");
    }

    @Test
    @DisplayName("첫 페이지에서는 '처음·이전'이, 마지막에서는 '다음·끝'이 없다")
    void edgeNavigationIsOmitted() {
        String first = renderDefault(pageOf(1, 5), "");
        assertThat(first).doesNotContain("첫 페이지");
        assertThat(first).doesNotContain("이전 페이지");
        assertThat(first).contains("다음 페이지").contains("마지막 페이지");

        String last = renderDefault(pageOf(5, 5), "");
        assertThat(last).contains("첫 페이지").contains("이전 페이지");
        assertThat(last).doesNotContain("다음 페이지");
        assertThat(last).doesNotContain("마지막 페이지");
    }

    @Test
    @DisplayName("window 는 10개를 넘지 않고, 마지막 구간에서도 10개를 유지한다")
    void windowStaysWithinSize() {
        // 100쪽 중 50쪽 — 번호는 정확히 10개
        assertThat(countNumberCells(renderDefault(pageOf(50, 100), ""))).isEqualTo(10);
        // 마지막 구간(100쪽 중 98쪽) — 오른쪽이 잘려도 왼쪽으로 채워 10개를 유지한다
        assertThat(countNumberCells(renderDefault(pageOf(98, 100), ""))).isEqualTo(10);
        // 전체가 window 보다 작으면 전체 쪽수만큼만
        assertThat(countNumberCells(renderDefault(pageOf(2, 4), ""))).isEqualTo(4);
    }

    @Test
    @DisplayName("renderWindow 로 window 크기를 바꿀 수 있다")
    void customWindowSize() {
        String html = render(pageOf(50, 100), "/admin/system/board", "",
                             "renderWindow(${page}, ${baseUrl}, 5)");
        assertThat(countNumberCells(html)).isEqualTo(5);
    }

    @Test
    @DisplayName("인라인 on* 핸들러를 만들지 않는다 — CSP strict-dynamic 하에서 조용히 무시된다")
    void noInlineHandlers() {
        String html = renderDefault(pageOf(3, 5), "&keyword=x");
        assertThat(html).doesNotContainPattern("\\son[a-z]+\\s*=");
    }

    /** 번호 셀(현재 페이지 span + 나머지 a) 개수. */
    private static int countNumberCells(String html) {
        return html.split("min-w-8", -1).length - 1
             - (html.contains("첫 페이지") ? 1 : 0)
             - (html.contains("이전 페이지") ? 1 : 0)
             - (html.contains("다음 페이지") ? 1 : 0)
             - (html.contains("마지막 페이지") ? 1 : 0);
    }
}
