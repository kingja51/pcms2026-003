package com.gonet.primary.system.login.controller;

import com.gonet.common.audit.AuditLogger;
import com.gonet.logging.log.mapper.LoginLogMapper;
import com.gonet.logging.security.service.SecurityLogger;
import com.gonet.primary.member.mapper.MemberMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 회원 로그인 실패 핸들러.
 *
 * <p>실패 시 {@code tb_member.login_fail_count} +1, {@link #FAIL_LIMIT}회 도달 시
 * {@link #LOCK_MINUTES}분 자동 잠금. Admin 측 동작과 동일.
 */
@Component
public class MemberLoginFailureHandler extends AbstractLoginFailureHandler {

    private final MemberMapper memberMapper;

    public MemberLoginFailureHandler(LoginLogMapper loginLogMapper,
                                      AuditLogger auditLogger,
                                      SecurityLogger securityLogger,
                                      MemberMapper memberMapper) {
        super(loginLogMapper, auditLogger, securityLogger);
        this.memberMapper = memberMapper;
    }

    @Override
    protected String failureRedirectBase() {
        return "/member/login";
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
            int updated = memberMapper.incrementLoginFailAndMaybeLock(id, FAIL_LIMIT, LOCK_MINUTES);
            if (updated == 0) return FailureDetails.none();
            Integer n = memberMapper.findLoginFailCountByLoginId(id);
            if (n != null && n >= FAIL_LIMIT) {
                // 5회 실패 잠금 발동 — captcha_required_yn='Y' 강제. 다음 로그인 성공 시점까지
                // 모든 로그인 시도에 CAPTCHA 통과 필수. 잠금 해제 후에도 본 플래그는 유지.
                try {
                    memberMapper.setCaptchaRequiredByLoginId(id, "Y");
                } catch (Exception ignored) { /* 실패해도 잠금은 정상 발동 */ }
                return FailureDetails.lockedNow(LOCK_MINUTES);
            }
            return (n != null && n > 0) ? FailureDetails.count(n) : FailureDetails.none();
        } catch (Exception ex) {
            log.warn("MEMBER_FAIL_COUNT update error loginId={} reason={}",
                loginId, ex.getMessage());
            return FailureDetails.none();
        }
    }
}
