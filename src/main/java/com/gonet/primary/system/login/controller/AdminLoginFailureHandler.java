package com.gonet.primary.system.login.controller;

import com.gonet.common.audit.AuditLogger;
import com.gonet.logging.log.mapper.LoginLogMapper;
import com.gonet.logging.security.service.SecurityLogger;
import com.gonet.primary.admin.mapper.AdminMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 관리자 로그인 실패 핸들러.
 *
 * <p>실패 시 {@code tb_admin.login_fail_count} +1 및 {@link #FAIL_LIMIT} 회 도달 시
 * {@link #LOCK_MINUTES}분 자동 잠금. 리다이렉트 URL 에 {@code failCount}/{@code locked}
 * 파라미터를 포함해 폼이 사용자에게 표시할 수 있게 한다.
 *
 * <p>loginId 미존재(오탈자) 시 카운트 미기록 — 사용자 enumeration 방지.
 */
@Component
public class AdminLoginFailureHandler extends AbstractLoginFailureHandler {

    private final AdminMapper adminMapper;

    public AdminLoginFailureHandler(LoginLogMapper loginLogMapper,
                                     AuditLogger auditLogger,
                                     SecurityLogger securityLogger,
                                     AdminMapper adminMapper) {
        super(loginLogMapper, auditLogger, securityLogger);
        this.adminMapper = adminMapper;
    }

    @Override
    protected String failureRedirectBase() {
        return "/admin/login";
    }

    @Override
    protected FailureDetails captureFailureDetails(HttpServletRequest req, String loginId, String reasonCode) {
        if (loginId == null || loginId.isBlank()) return FailureDetails.none();

        // 이미 잠금(LockedException) 인 경우: 카운트 증가 없이 locked 플래그만 전달
        if ("LOCKED".equals(reasonCode)) {
            return FailureDetails.lockedNow(LOCK_MINUTES);
        }

        try {
            String id = loginId.trim().toLowerCase();
            int updated = adminMapper.incrementLoginFailAndMaybeLock(id, FAIL_LIMIT, LOCK_MINUTES);
            if (updated == 0) return FailureDetails.none();    // 존재하지 않는 loginId
            Integer n = adminMapper.findLoginFailCountByLoginId(id);
            if (n != null && n >= FAIL_LIMIT) {
                // 5회 실패 잠금 발동 — captcha_required_yn='Y' 강제. 다음 로그인 성공 시점까지
                // 모든 로그인 시도에 CAPTCHA 통과 필수. 잠금 해제 후에도 본 플래그는 유지.
                try {
                    adminMapper.setCaptchaRequiredByLoginId(id, "Y");
                } catch (Exception ignored) { /* 실패해도 잠금은 정상 발동 */ }
                return FailureDetails.lockedNow(LOCK_MINUTES);
            }
            return (n != null && n > 0) ? FailureDetails.count(n) : FailureDetails.none();
        } catch (Exception ex) {
            log.warn("ADMIN_FAIL_COUNT update error loginId={} reason={}",
                loginId, ex.getMessage());
            return FailureDetails.none();
        }
    }
}
