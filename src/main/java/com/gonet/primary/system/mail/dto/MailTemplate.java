package com.gonet.primary.system.mail.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_mail_template 1건 — 메일 템플릿 (Thymeleaf HTML).
 *
 * <p>{@code templateCode} 는 코드 참조용 식별자 (예: MEMBER_WELCOME / PASSWORD_RESET).
 * <p>{@code bodyHtml} 은 Thymeleaf 표현식을 포함한 완전한 HTML.
 * {@link com.gonet.common.mail.MailService#sendFromTemplate} 가
 * StringTemplateResolver 기반 엔진으로 렌더링.
 */
@Getter
@Setter
public class MailTemplate extends BaseEntity implements SoftDeletable, UseFlagged {

    public static final String CODE_MEMBER_WELCOME              = "MEMBER_WELCOME";
    public static final String CODE_PASSWORD_CHANGED            = "PASSWORD_CHANGED";
    public static final String CODE_PASSWORD_RESET              = "PASSWORD_RESET";
    public static final String CODE_ACCOUNT_DORMANT             = "ACCOUNT_DORMANT";
    public static final String CODE_MEMBER_WITHDRAW             = "MEMBER_WITHDRAW";
    /** 휴면 전환 30일 전 알림. */
    public static final String CODE_ACCOUNT_DORMANT_NOTICE_30D  = "ACCOUNT_DORMANT_NOTICE_30D";
    /** 휴면 전환 7일 전 알림. */
    public static final String CODE_ACCOUNT_DORMANT_NOTICE_7D   = "ACCOUNT_DORMANT_NOTICE_7D";
    /** 휴면 전환 1일 전 알림. */
    public static final String CODE_ACCOUNT_DORMANT_NOTICE_1D   = "ACCOUNT_DORMANT_NOTICE_1D";
    /** 휴면 전환 완료 알림. */
    public static final String CODE_ACCOUNT_DORMANT_TRANSFERRED = "ACCOUNT_DORMANT_TRANSFERRED";
    /** 휴면 복원 완료 알림. */
    public static final String CODE_ACCOUNT_DORMANT_RESTORED    = "ACCOUNT_DORMANT_RESTORED";

    /** 휴면 해제 인증번호(수단 B) — 모델: memberName, loginId, otpCode. */
    public static final String CODE_ACCOUNT_DORMANT_OTP          = "ACCOUNT_DORMANT_OTP";

    private String  mailTemplateId;
    private String  templateCode;
    private String  templateName;
    private String  subject;
    private String  bodyHtml;
    private String  senderEmail;
    private String  senderName;
    private String  description;
    private String  variablesHint;
    private String  useYn;
    private String  deleteYn;
}
