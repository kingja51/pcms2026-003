package com.gonet.config.interceptor;

import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.login.service.TwoFactorSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 2FA 관문 강제 — 관리자 영역({@code /admin/**}) 접근 시 2FA 활성 사용자가
 * 코드 검증을 완료하지 않았다면 {@code /admin/login/2fa} 로 강제 리다이렉트.
 *
 * <p>로그인 직후 AdminLoginSuccessHandler 가 한 번 보내도, 사용자가 주소창에
 * /admin/... 을 직접 입력해 우회하는 것을 차단.
 */
@Component
public class TwoFactorEnforcementInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorEnforcementInterceptor.class);

    /** 2FA 미통과 상태에서도 접근 허용해야 하는 경로 (관문 진입/로그아웃/에러). */
    private static final Set<String> BYPASS = Set.of(
        "/admin/login", "/admin/login/2fa", "/admin/logout", "/error"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.info("===TwoFactorEnforcementInterceptor.preHandle executed for URI: {}", request.getRequestURI());
        String uri = request.getRequestURI();
        if (uri == null) return true;
        if (BYPASS.contains(uri)) return true;

        Authentication authn = SecurityContextHolder.getContext().getAuthentication();
        if (authn == null || !authn.isAuthenticated()) return true;
        if (!(authn.getPrincipal() instanceof CustomUserDetails ud)) return true;
        if (!ud.isTwoFactorEnabled()) return true;

        if (TwoFactorSession.isPassed(request)) return true;

        try {
            log.info("2FA gate redirect: uri={} user={}", uri, ud.getUsername());
            response.sendRedirect("/admin/login/2fa");
        } catch (Exception e) {
            log.warn("2FA redirect failed: {}", e.getMessage());
        }
        return false;
    }
}
