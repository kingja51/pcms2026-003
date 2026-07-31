package com.gonet.primary.system.login.controller;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.logging.log.mapper.LoginLogMapper;
import com.gonet.primary.admin.mapper.AdminMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.login.service.AdminLoginGuard;
import com.gonet.primary.system.login.service.LastLoginTracker;
import com.gonet.primary.system.login.service.TwoFactorSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 관리자 로그인 성공 핸들러.
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link AdminLoginGuard#hasStaffOrAbove(String)} — ROLE_STAFF 이상 체크. 실패 시 403 + 로그아웃</li>
 *   <li>{@link AdminLoginGuard#ipAllowed(String, String)} — tb_admin_allow_ip 검증</li>
 *   <li>2FA 활성 사용자 → /admin/login/2fa 관문 (세션 PASSED 플래그)</li>
 *   <li>그 외 → SavedRequest 또는 /admin/dashboard</li>
 * </ol>
 */
@Component
public class AdminLoginSuccessHandler extends AbstractLoginSuccessHandler {

    private final AdminLoginGuard guard;
    private final AdminMapper    adminMapper;

    public AdminLoginSuccessHandler(LoginLogMapper loginLogMapper,
                                     AuditLogger auditLogger,
                                     LastLoginTracker lastLoginTracker,
                                     AdminLoginGuard guard,
                                     AdminMapper adminMapper) {
        super(loginLogMapper, auditLogger, lastLoginTracker);
        this.guard = guard;
        this.adminMapper = adminMapper;
        setDefaultTargetUrl("/admin/dashboard");
    }

    @Override
    protected String defaultTarget() {
        return "/admin/dashboard";
    }

    /**
     * 로그인 성공 시 captcha_required_yn='N' 복귀.
     *
     * <p>001 은 여기서 user_type 에 따라 admin / employee 매퍼로 분기했다.
     * 003 은 직원을 로그인 주체에서 제외했으므로(D7) admin 단일 경로다.
     */
    @Override
    protected void clearCaptchaRequired(CustomUserDetails ud) {
        if (ud == null || ud.getUsername() == null) return;
        adminMapper.setCaptchaRequiredByLoginId(ud.getUsername().trim().toLowerCase(), "N");
    }

    @Override
    protected boolean onPostAuthChecks(HttpServletRequest req, HttpServletResponse res,
                                        CustomUserDetails ud) throws IOException {
        // 1) ROLE_STAFF 이상 체크 — closure 확장된 role_ids CSV 에 ROLE_STAFF role_id 포함 여부
        if (!guard.hasStaffOrAbove(ud.getRoleIds())) {
            denyAndLog(req, res, ud, "ROLE_STAFF_REQUIRED");
            return false;
        }

        // 2) IP 화이트리스트 체크
        String clientIp = AuditContext.ip();
        if (!guard.ipAllowed(ud.getUserId(), clientIp)) {
            denyAndLog(req, res, ud, "IP_NOT_ALLOWED");
            return false;
        }

        // 3) 2FA 분기
        if (ud.isTwoFactorEnabled()) {
            TwoFactorSession.markPending(req);
            res.sendRedirect("/admin/login/2fa");
            return false;
        }
        TwoFactorSession.markPassed(req);
        return true;
    }

    private void denyAndLog(HttpServletRequest req, HttpServletResponse res,
                             CustomUserDetails ud, String reasonCode) throws IOException {
        auditLogger.write(AuditEvent.of("LOGIN_DENY", "tb_admin")
            .withActor(ud.getUserId(), ud.getUserType(), ud.getUsername())
            .withHttp(req.getMethod(), req.getRequestURI(),
                      AuditContext.ip(), req.getHeader("User-Agent"))
            .withTarget(ud.getUserId())
            .withResult("FAIL")
            .withAfter("{\"reason\":\"" + reasonCode + "\"}"));
        log.warn("ADMIN_LOGIN_DENY loginId={} ip={} reason={}",
            ud.getUsername(), AuditContext.ip(), reasonCode);

        // 세션 무효화 + 로그인 폼으로 이동
        if (req.getSession(false) != null) req.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        res.sendRedirect("/admin/login?error=" + reasonCode);
    }
}
