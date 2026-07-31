package com.gonet.primary.system.login.controller;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.logging.log.dto.LoginLog;
import com.gonet.logging.log.mapper.LoginLogMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.login.service.LastLoginTracker;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * 로그인 성공 핸들러 공통 베이스 — log_login + audit + SavedRequest 안전성 로직 공유.
 *
 * <p>구체 클래스는:
 * <ul>
 *   <li>{@link #defaultTarget()} : 로그인 후 기본 이동 경로</li>
 *   <li>{@link #onPostAuthChecks(HttpServletRequest, HttpServletResponse, CustomUserDetails)} :
 *       2FA, IP, 권한 등 추가 검증. {@code true} 반환 시 통상 흐름 진행, {@code false} 면
 *       메서드 내부에서 직접 응답 처리(리다이렉트/세션 무효화) 후 종료.</li>
 * </ul>
 */
public abstract class AbstractLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    protected static final Logger log = LoggerFactory.getLogger(AbstractLoginSuccessHandler.class);

    /** 로그인 성공 후 SavedRequest 로 따라가지 않을 경로 (브라우저 잔존 SW/자동요청 방어). */
    private static final Set<String> BLOCKED_PATHS = Set.of(
        "/sw.js", "/favicon.ico", "/robots.txt",
        "/manifest.json", "/manifest.webmanifest",
        "/.well-known/appspecific/com.chrome.devtools.json"
    );

    /** 정적 자원 확장자 — HTML 페이지가 아닌 부차 요청은 SavedRequest 로 따라가지 않음. */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
        ".js", ".css", ".ico", ".map",
        ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp",
        ".woff", ".woff2", ".ttf", ".eot"
    );

    protected final LoginLogMapper    loginLogMapper;
    protected final AuditLogger       auditLogger;
    protected final LastLoginTracker  lastLoginTracker;
    private final   RequestCache      requestCache = new HttpSessionRequestCache();

    protected AbstractLoginSuccessHandler(LoginLogMapper loginLogMapper,
                                          AuditLogger auditLogger,
                                          LastLoginTracker lastLoginTracker) {
        this.loginLogMapper   = loginLogMapper;
        this.auditLogger      = auditLogger;
        this.lastLoginTracker = lastLoginTracker;
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req,
                                        HttpServletResponse res,
                                        Authentication auth) throws IOException, ServletException {
        CustomUserDetails ud = (CustomUserDetails) auth.getPrincipal();

        writeLoginLog(req, ud);
        lastLoginTracker.track(ud.getUserType(), ud.getUserId(), AuditContext.ip());
		
		String targetEntity = "tb_" + ud.getUserType().toLowerCase();
		// STAFF인 경우 tb_admin으로 강제 매핑
		if ("STAFF".equals(ud.getUserType())) {
			targetEntity = "tb_admin";
		}
		auditLogger.write(AuditEvent.of("LOGIN", targetEntity)
			.withActor(ud.getUserId(), ud.getUserType(), ud.getUsername()) 
            .withHttp(req.getMethod(), req.getRequestURI(), AuditContext.ip(), req.getHeader("User-Agent"))
            .withTarget(ud.getUserId())
            .withResult("SUCCESS"));
        log.info("===LOGIN_SUCCESS type={} userId={} loginId={} ip={}", ud.getUserType(), ud.getUserId(), ud.getUsername(), AuditContext.ip());

        if (!onPostAuthChecks(req, res, ud)) {
            return; // 하위 클래스가 응답 처리 완료
        }

        // CAPTCHA 강제 플래그 해제 — 5회 실패 잠금 발동 후 첫 성공 시점.
        // 도메인별 mapper 호출은 하위 클래스가 정의 (admin/employee/member 분리).
        try {
            clearCaptchaRequired(ud);
        } catch (Exception ex) {
            log.warn("CAPTCHA_REQUIRED clear failed loginId={} reason={}", ud.getUsername(), ex.getMessage());
        }

        // (1) 명시 returnUrl 파라미터 우선 — 폼 hidden input 으로 POST 동반.
        //     보호된 URL 진입 시 AuthExceptionHandlers 가 /admin/login?returnUrl= 로 보낸 흐름.
        //     SavedRequest 보다 우선 적용해야 사용자 의도와 일치.
        String returnUrl = sanitizeReturnUrl(req.getParameter("returnUrl"));
        if (returnUrl != null) {
            // SavedRequest 잔존분 정리 — returnUrl 채택 시 동반 SavedRequest 는 폐기
            requestCache.removeRequest(req, res);
            log.info("Login success — honoring returnUrl param: {}", returnUrl);
            getRedirectStrategy().sendRedirect(req, res, returnUrl);
            return;
        }

        // (2) SavedRequest 가 부적절(static 자원/의심 경로)이면 폐기
        SavedRequest saved = requestCache.getRequest(req, res);
        if (saved != null && isUnsafeTarget(saved.getRedirectUrl())) {
            log.info("Discard unsafe saved target after login: {}", saved.getRedirectUrl());
            requestCache.removeRequest(req, res);
            saved = null;
        }
        if (saved != null) {
            super.onAuthenticationSuccess(req, res, auth);
            return;
        }
        getRedirectStrategy().sendRedirect(req, res, defaultTarget());
    }

    /**
     * 사용자가 폼에 동반한 returnUrl 을 검증한다. 다음 조건을 모두 만족해야 채택:
     * <ul>
     *   <li>blank 가 아닐 것</li>
     *   <li>{@code /} 로 시작하는 상대 경로 (외부 도메인 / 스킴 / protocol-relative {@code //} 차단)</li>
     *   <li>backslash / CR / LF 미포함 (header injection 차단)</li>
     *   <li>로그인/로그아웃/에러 경로 자체로 되돌아가지 않을 것</li>
     *   <li>{@link #isUnsafeTarget(String)} 가 false (정적 자원/sw.js 등 차단)</li>
     * </ul>
     * 부적격이면 null 반환 → SavedRequest / defaultTarget 흐름으로 위임.
     */
    private static String sanitizeReturnUrl(String raw) {
        if (raw == null) return null;
        String url = raw.trim();
        if (url.isEmpty()) return null;
        if (!url.startsWith("/"))    return null;   // 절대경로/외부 차단
        if (url.startsWith("//"))    return null;   // protocol-relative 차단
        if (url.contains("\\") || url.contains("\r") || url.contains("\n")) return null;
        // 로그인/로그아웃/에러 경로로 되돌아가는 루프 차단
        if (url.startsWith("/admin/login")  || url.startsWith("/admin/logout")
            || url.startsWith("/member/login") || url.startsWith("/member/logout")
            || url.startsWith("/error")) return null;
        if (isUnsafeTarget(url))      return null;
        return url;
    }

    /** 구체 클래스가 반환하는 기본 이동 경로 (예: /admin/dashboard, /member/mypage). */
    protected abstract String defaultTarget();

    /**
     * 추가 검증 훅 — 2FA/IP/권한 등.
     * @return true=통상 흐름 진행, false=하위 클래스가 응답 처리 완료
     */
    protected boolean onPostAuthChecks(HttpServletRequest req, HttpServletResponse res,
                                        CustomUserDetails ud) throws IOException {
        return true;
    }

    /**
     * CAPTCHA 강제 플래그 해제 hook — 로그인 성공 시 captcha_required_yn='N' 복귀.
     * 도메인별 mapper 가 다르므로 구체 클래스가 정의 (admin / member / employee).
     * 기본 구현은 no-op — 미오버라이드 도메인은 영향 없음.
     */
    protected void clearCaptchaRequired(CustomUserDetails ud) {
        /* default no-op */
    }

    private void writeLoginLog(HttpServletRequest req, CustomUserDetails ud) {
        LoginLog entry = new LoginLog();
        entry.setUserId(ud.getUserId());
        entry.setUserType(ud.getUserType());
        entry.setLoginId(ud.getUsername());
        entry.setClientIp(AuditContext.ip());
        entry.setUserAgent(req.getHeader("User-Agent"));
        entry.setResult(LoginLog.RESULT_SUCCESS);
        entry.setSessionId(req.getSession(false) != null ? req.getSession(false).getId() : null);
        entry.setCreatedBy(ud.getUserId());
        entry.setCreatedIp(AuditContext.ip());
        entry.setUpdatedBy(ud.getUserId());
        entry.setUpdatedIp(AuditContext.ip());
        try {
            loginLogMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("log_login insert failed (SUCCESS): {}", ex.getMessage());
        }
    }

    private static boolean isUnsafeTarget(String url) {
        if (url == null || url.isBlank()) return true;
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException ex) {
            return true;
        }
        if (path == null || path.isBlank()) return true;
        if (BLOCKED_PATHS.contains(path)) return true;
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot > slash) {
            String ext = path.substring(dot).toLowerCase();
            return BLOCKED_EXTENSIONS.contains(ext);
        }
        return false;
    }
}
