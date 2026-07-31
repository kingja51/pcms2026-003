package com.gonet.primary.system.login.controller;

import com.gonet.common.audit.AuditLogger;
import com.gonet.logging.log.mapper.LoginLogMapper;
import com.gonet.primary.member.mapper.MemberMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.login.service.LastLoginTracker;
import org.springframework.stereotype.Component;

/**
 * 회원 로그인 성공 핸들러.
 *
 * <p>로그인 성공 후 SavedRequest 가 없으면 {@code /member/mypage} 로 이동.
 * 회원 도메인은 2FA/IP 화이트리스트 미적용 — 추가 검증 훅 미구현.
 *
 * <p>{@link #clearCaptchaRequired} — 5회 실패 잠금 후 첫 로그인 성공 시점에
 * {@code captcha_required_yn='N'} 으로 복귀. 다음 로그인부터 CAPTCHA 면제.
 */
@Component
public class MemberLoginSuccessHandler extends AbstractLoginSuccessHandler {

    private final MemberMapper memberMapper;

    public MemberLoginSuccessHandler(LoginLogMapper loginLogMapper,
                                      AuditLogger auditLogger,
                                      LastLoginTracker lastLoginTracker,
                                      MemberMapper memberMapper) {
        super(loginLogMapper, auditLogger, lastLoginTracker);
        this.memberMapper = memberMapper;
        setDefaultTargetUrl("/member/mypage");
    }

    @Override
    protected String defaultTarget() {
        return "/member/mypage";
    }

    @Override
    protected void clearCaptchaRequired(CustomUserDetails ud) {
        if (ud == null || ud.getUsername() == null) return;
        memberMapper.setCaptchaRequiredByLoginId(ud.getUsername().trim().toLowerCase(), "N");
    }
}
