package com.gonet.config.filter;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.captcha.CaptchaGuard;
import com.gonet.primary.admin.mapper.AdminMapper;
import com.gonet.primary.system.login.dto.LoginCredential;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 로그인 폼 POST 진입 시 loginId/password 형식을 서버에서 1차 검증하는 필터.
 *
 * <p>Spring Security 의 {@code UsernamePasswordAuthenticationFilter} 직전에 실행되어,
 * BCrypt 비교 · 잠금 카운트 갱신 · DB 조회 전에 명백한 규칙 위반 요청을 짧은 경로로 반려한다.
 * <p>정책 위반 시 {@code ?error=INVALID_FORMAT} 으로 리다이렉트 — 기존 로그인 페이지가
 * 해당 errorCode 를 이미 처리한다.
 *
 * <p>검증 대상: {@code POST /admin/login}, {@code POST /member/login}.
 * <br>검증 규칙: {@link LoginCredential} 의 Bean Validation 제약.
 *
 * <p><b>RateLimit 와의 상호작용</b>: 본 필터는 {@code RateLimitFilter} 보다 뒤에 위치
 * ({@link com.gonet.config.security.SecurityConfig} 의 addFilterBefore 순서 참고).
 * 따라서 형식 위반 요청도 RateLimit IP/loginId 토큰을 1개 소비한 뒤에 reject 된다 —
 * 의도된 동작 (스캐너의 dummy POST 도 토큰 차감되어 burst 폭주 방어).
 */
@Component
public class LoginFormatValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginFormatValidationFilter.class);

    private static final String ADMIN_LOGIN  = "/admin/login";
    private static final String MEMBER_LOGIN = "/member/login";

    private final Validator validator;
    private final CaptchaGuard captchaGuard;
    private final AdminMapper    adminMapper;

    public LoginFormatValidationFilter(Validator validator,
                                        CaptchaGuard captchaGuard,
                                        AdminMapper adminMapper) {
        this.validator = validator;
        this.captchaGuard = captchaGuard;
        this.adminMapper = adminMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        log.info("===LoginFormatValidationFilter intercepting request for URI: {}", req.getRequestURI());

        if (!"POST".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }
        String uri = req.getRequestURI();
        if (!(ADMIN_LOGIN.equals(uri) || MEMBER_LOGIN.equals(uri))) {
            chain.doFilter(req, res);
            return;
        }

        log.info("===[LoginFormatValidation] 로그인 형식 검증 시작 - uri={}, ip={}", uri, AuditContext.ip());
        LoginCredential c = new LoginCredential();
        c.setLoginId(req.getParameter("loginId"));
        c.setPassword(req.getParameter("password"));

        Set<ConstraintViolation<LoginCredential>> violations = validator.validate(c);
        if (violations.isEmpty()) {
            log.info("===[LoginFormatValidation] 로그인 형식 검증 통과 - uri={}, ip={}", uri, AuditContext.ip());

            // CAPTCHA — 해당 loginId 의 captcha_required_yn='Y' 이면 강제 검증.
            // 5회 실패 잠금 발동 후 첫 성공 시점까지 유지. (P2 는 admin 도메인만 — member 는 P5)든 'Y' 면 강제.
            if (captchaGuard != null && requiresCaptcha(c.getLoginId())
                && !captchaGuard.verifyOrFail(req)) {
                log.warn("===LOGIN_CAPTCHA_REQUIRED uri={} ip={}", uri, AuditContext.ip());
                res.sendRedirect(uri + "?error=CAPTCHA_REQUIRED");
                return;
            }

            chain.doFilter(req, res);
            return;
        }

        String failedFields = violations.stream()
            .map(v -> v.getPropertyPath().toString())
            .distinct()
            .sorted()
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        log.info("===LOGIN_FORMAT_REJECT uri={} ip={} fields={}",
            uri, AuditContext.ip(), failedFields);

        res.sendRedirect(uri + "?error=INVALID_FORMAT");
    }

    /**
     * 입력된 loginId 에 대해 captcha_required_yn='Y' 인지 확인.
     * P2 는 admin 만 조회한다 — member 는 P5 에서 추가하고, employee 는 로그인 주체가 아니다(D7).
     * 한 곳이라도 'Y' 면 true. DB 장애 시 false 반환 — UX 우선 (로그인 자체는 진행).
     */
    private boolean requiresCaptcha(String loginId) {
        if (loginId == null || loginId.isBlank()) return false;
        String id = loginId.trim().toLowerCase();
        try {
            if ("Y".equalsIgnoreCase(adminMapper.findCaptchaRequiredByLoginId(id)))    return true;
        } catch (Exception ex) {
            log.warn("CAPTCHA_REQUIRED lookup error reason={}", ex.getMessage());
        }
        return false;
    }
}
