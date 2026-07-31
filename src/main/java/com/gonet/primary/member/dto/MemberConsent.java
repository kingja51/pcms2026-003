package com.gonet.primary.member.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_member_consent — 약관 동의 이력 1건.
 *
 * <p>{@code consentType}: TERMS/PRIVACY/MARKETING/SMS/EMAIL/THIRD_PARTY.
 */
@Getter
@Setter
public class MemberConsent extends BaseEntity {

    public static final String TYPE_TERMS        = "TERMS";
    public static final String TYPE_PRIVACY      = "PRIVACY";
    public static final String TYPE_MARKETING    = "MARKETING";
    public static final String TYPE_SMS          = "SMS";
    public static final String TYPE_EMAIL        = "EMAIL";
    public static final String TYPE_THIRD_PARTY  = "THIRD_PARTY";

    private String        memberConsentId;
    private String        memberId;
    private String        consentType;
    private String        consentVersion;
    private String        agreeYn;
    private LocalDateTime agreedAt;
    private String        clientIp;
    private String        userAgent;
}
