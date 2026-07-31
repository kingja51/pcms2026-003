package com.gonet.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 인증/인가 예외 처리 — 비로그인 vs 권한부족 분기.
 *
 * <p>정책:
 * <ul>
 *   <li>익명 사용자가 보호된 URL 접근 → 로그인 페이지로 redirect (HTML)
 *       또는 401 JSON (API/AJAX)</li>
 *   <li>인증된 사용자가 권한 없는 URL 접근 → 403 페이지 (HTML)
 *       또는 403 JSON (API/AJAX)</li>
 * </ul>
 *
 * <p>로그인 페이지 결정:
 * <ul>
 *   <li>{@code /admin/**} → {@code /admin/login}</li>
 *   <li>그 외 → {@code /member/login}</li>
 * </ul>
 *
 * <p>JSON/AJAX 판정: {@code Accept} 헤더가 {@code application/json} 또는
 * {@code X-Requested-With: XMLHttpRequest}, 또는 URI 가 {@code /api/} 로 시작.
 *
 * <p><b>htmx 판정</b>: {@code HX-Request: true} 헤더. htmx 의 XHR 은 302 redirect 를
 * 투명하게 따라가 로그인 페이지(200) HTML 을 조각 target 에 그대로 swap 하는 문제가 있다.
 * htmx 요청에는 body 대신 {@code HX-Redirect} 헤더를 실어 브라우저 전체 페이지 전환을
 * 유도하고(미인증), 인가 거부는 {@code HX-Reswap: none} 으로 swap 자체를 차단한다.
 */
@Configuration
public class AuthExceptionHandlers {

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (req, res, ex) -> {
            String loginUrl = resolveLoginUrl(req) + "?returnUrl=" + encodeReturnUrl(req);
            // htmx 우선 — 조각 target 에 로그인 페이지가 swap 되지 않도록 HX-Redirect 로
            // 전체 페이지를 로그인으로 전환. status 401 (htmx 는 HX-Redirect 를 우선 처리).
            if (isHtmxRequest(req)) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setHeader("HX-Redirect", loginUrl);
                return;
            }
            if (isApiRequest(req)) {
                writeJson(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"ok\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}");
                return;
            }
            res.sendRedirect(loginUrl);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (req, res, ex) -> {
            // htmx 우선 — 403 body(4xx.html)가 조각 target 에 swap 되는 것을 명시적으로 차단.
            // HX-Reswap:none 으로 swap 을 막고 403 상태만 통지(htmx:responseError 발생).
            if (isHtmxRequest(req)) {
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                res.setHeader("HX-Reswap", "none");
                return;
            }
            if (isApiRequest(req)) {
                writeJson(res, HttpServletResponse.SC_FORBIDDEN,
                    "{\"ok\":false,\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}");
                return;
            }
            // sendError 로 컨테이너 오류 메커니즘 경유 — jakarta.servlet.error.* 속성이
            // 채워진 상태로 /error 에 도달해 4xx.html 이 렌더된다.
            // (setStatus + 수동 forward 는 속성 부재로 status=999 오동작 — P2 이관 이슈)
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
        };
    }

    private static boolean isHtmxRequest(HttpServletRequest req) {
        return "true".equalsIgnoreCase(req.getHeader("HX-Request"));
    }

    private static boolean isApiRequest(HttpServletRequest req) {
        String uri = req.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) return true;
        String xrw = req.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(xrw)) return true;
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)
                && !accept.contains(MediaType.TEXT_HTML_VALUE)) {
            return true;
        }
        return false;
    }

    private static String resolveLoginUrl(HttpServletRequest req) {
        String uri = req.getRequestURI();
        if (uri != null && uri.startsWith("/admin/")) return "/admin/login";
        return "/member/login";
    }

    private static String encodeReturnUrl(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String qs  = req.getQueryString();
        String full = (qs == null || qs.isBlank()) ? uri : uri + "?" + qs;
        return URLEncoder.encode(full == null ? "/" : full, StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpServletResponse res, int status, String body) {
        try {
            res.setStatus(status);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write(body);
        } catch (Exception ignore) {
            // best effort — 응답이 이미 commit 되었으면 무시
        }
    }
}
