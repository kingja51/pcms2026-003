package com.gonet.primary.member.oauth2.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * provider userinfo 정규화 결과 — 가입 폼 prefill 및 매핑 INSERT 입력값.
 *
 * <p>세션에 보관되므로 {@link Serializable} 구현. PII (이메일/이름) 는 평문이지만
 * 가입 완료 시 즉시 소비·삭제하므로 저장소에는 절대 누적되지 않는다.
 */
@Getter
@Setter
public class ExternalProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** HttpSession 속성 키 — OAuth2Properties.externalProfileSessionKey 와 동일. */
    public static final String SESS_KEY = "PCMS_OAUTH2_EXTERNAL";

    private OAuth2Provider provider;
    private String         providerUserId;
    private String         email;
    private String         name;
    private String         nickname;

    public boolean hasIdentity() {
        return provider != null && providerUserId != null && !providerUserId.isBlank();
    }
}
