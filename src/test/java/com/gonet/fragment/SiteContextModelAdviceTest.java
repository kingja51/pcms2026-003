package com.gonet.fragment;

import com.gonet.primary.system.site.controller.SiteContextModelAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code pageQuery} 모델 속성 검증 — {@code fragments/pagination} 이 이 값에 전적으로 기댄다.
 *
 * <p>여기가 틀리면 증상이 "페이지를 넘기면 검색이 풀린다" 로 나타난다. 화면은 200 이고
 * 예외도 없어서 눈으로는 잘 안 잡힌다.
 *
 * <p>advice 의 다른 모델 속성(themeClass·배너·팝업)은 DB 를 타므로 여기서 다루지 않는다.
 * {@code pageQuery} 는 {@code HttpServletRequest} 만 보므로 협력자를 mock 으로 두고
 * 이 메서드만 단독 검사한다.
 */
class SiteContextModelAdviceTest {

    /** 생성자 인자는 pageQuery 계산에 관여하지 않는다 — 전부 mock. */
    private SiteContextModelAdvice advice() {
        java.lang.reflect.Constructor<?> ctor =
            SiteContextModelAdvice.class.getDeclaredConstructors()[0];
        Object[] args = new Object[ctor.getParameterCount()];
        Class<?>[] types = ctor.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            args[i] = types[i].isPrimitive() ? 0 : mock(types[i]);
        }
        try {
            ctor.setAccessible(true);
            return (SiteContextModelAdvice) ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("SiteContextModelAdvice 생성 실패", e);
        }
    }

    private String pageQuery(String... nameValue) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        for (int i = 0; i < nameValue.length; i += 2) req.addParameter(nameValue[i], nameValue[i + 1]);
        return advice().injectPageQuery(req);
    }

    @Test
    @DisplayName("파라미터가 없으면 빈 문자열 — ?page=N 뒤에 아무것도 붙지 않는다")
    void emptyWhenNoParams() {
        assertThat(pageQuery()).isEmpty();
    }

    @Test
    @DisplayName("page 는 제거한다 — 조각이 새 번호를 붙이므로 남으면 ?page=2&page=5 가 된다")
    void dropsPageParam() {
        assertThat(pageQuery("page", "7")).isEmpty();
        assertThat(pageQuery("page", "7", "keyword", "abc")).isEqualTo("&keyword=abc");
    }

    @Test
    @DisplayName("한글·특수문자는 percent-encoding 한다 — 인코딩 없이 붙이면 링크가 깨진다")
    void encodesValues() {
        assertThat(pageQuery("keyword", "공지")).isEqualTo("&keyword=%EA%B3%B5%EC%A7%80");
        assertThat(pageQuery("keyword", "a&b=c")).isEqualTo("&keyword=a%26b%3Dc");
    }

    @Test
    @DisplayName("빈 검색조건은 버린다 — 안 그러면 URL 이 &dateFrom=&dateTo= 로 지저분해진다")
    void skipsBlankValues() {
        assertThat(pageQuery("keyword", "x", "dateFrom", "", "dateTo", "   "))
            .isEqualTo("&keyword=x");
    }

    @Test
    @DisplayName("같은 이름 다중 값(체크박스 필터)을 모두 보존한다")
    void keepsMultiValueParams() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("status", "DRAFT", "PUBLISHED");
        assertThat(advice().injectPageQuery(req)).isEqualTo("&status=DRAFT&status=PUBLISHED");
    }

    @Test
    @DisplayName("CSRF 토큰은 제외한다 — 페이지 링크에 박히면 Referer·히스토리·log_access 로 샌다")
    void dropsCsrfToken() {
        // Spring Security 는 토큰을 읽은 뒤에도 파라미터 맵에서 지우지 않는다.
        // POST 가 뷰를 직접 렌더하는 경로(검증 실패 시 폼 재렌더)에서 그대로 새어 나온다.
        assertThat(pageQuery("_csrf", "9f3a-secret-token", "keyword", "공지"))
            .isEqualTo("&keyword=%EA%B3%B5%EC%A7%80");
    }

    @Test
    @DisplayName("CSRF 파라미터명이 커스터마이즈돼도 제외한다 — 요청의 CsrfToken 에서 실제 이름을 읽는다")
    void dropsCustomNamedCsrfToken() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(CsrfToken.class.getName(),
            new DefaultCsrfToken("X-XSRF-TOKEN", "csrfParam", "secret"));
        req.addParameter("csrfParam", "secret");
        req.addParameter("keyword", "abc");

        assertThat(advice().injectPageQuery(req)).isEqualTo("&keyword=abc");
    }

    @Test
    @DisplayName("주입값에 XSS 벡터를 넣어도 인코딩돼 나온다 — 조각이 |…| 로 이어붙이므로 여기가 방어선")
    void encodesInjectionAttempt() {
        String q = pageQuery("keyword", "\"><script>alert(1)</script>");
        assertThat(q).doesNotContain("<").doesNotContain(">").doesNotContain("\"");
        assertThat(q).startsWith("&keyword=%22%3E%3Cscript%3E");
    }
}
