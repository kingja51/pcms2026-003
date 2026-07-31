package com.gonet.primary.system.login.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 회원 로그아웃 성공 핸들러 — 세션 파괴 후에도 siteCode 를 살려 redirect.
 *
 * <p>문제: Spring Security 의 {@code invalidateHttpSession(true)} 가 세션을 파괴하면
 * {@link com.gonet.primary.system.site.controller.SiteContextInterceptor} 의 session-sticky
 * siteCode 가 사라진다. 리다이렉트 대상이 {@code /member/login?logout} 이면 다음 요청에서
 * interceptor 의 6단계 해석이 모두 실패해 EMPTY 폴백으로 떨어진다.
 *
 * <p>해결: 로그아웃 폼이 hidden {@code siteCode} 필드를 제출하면 본 핸들러가 세션 파괴 전에
 * 읽어 redirect URL 쿼리 파라미터로 전달 → 다음 요청에서 1단계 '명시 파라미터' 해석이 성공.
 *
 * <p>보안: siteCode 값은 영문자/숫자/언더스코어 규약만 허용하여 URL 주입 차단.
 */
@Component
public class MemberLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(MemberLogoutSuccessHandler.class);

    private static final String BASE_REDIRECT = "/member/login?logout";
    private static final Pattern SAFE_SITE_CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,29}$");

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        String url = BASE_REDIRECT;

        String siteCode = request.getParameter("siteCode");
        if (siteCode != null) {
            siteCode = siteCode.trim();
            if (SAFE_SITE_CODE.matcher(siteCode).matches()) {
                url = url + "&siteCode=" + URLEncoder.encode(siteCode, StandardCharsets.UTF_8);
            } else if (!siteCode.isEmpty()) {
                log.info("===MEMBER_LOGOUT ignoring malformed siteCode param");
            }
        }
        log.info("===MEMBER_LOGOUT ok redirect={}", url);
        response.sendRedirect(request.getContextPath() + url);
    }
}
