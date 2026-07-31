package com.gonet.primary.member.oauth2.service;

/**
 * OAuth2 token/userinfo 교환 실패 — controller 가 사용자 친화 메시지로 변환.
 */
public class OAuth2Exception extends RuntimeException {

    public OAuth2Exception(String message) {
        super(message);
    }

    public OAuth2Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
