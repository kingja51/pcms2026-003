package com.gonet.primary.member.oauth2.service;

import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.OAuth2Provider;

/**
 * provider 별 OAuth2 token 교환 + userinfo 조회 + 정규화 — RestClient 기반.
 *
 * <p>인증 플로우 책임 분리:
 * <ul>
 *   <li>controller : authorize URL 생성 / state 발급·검증 / redirect 핸들링</li>
 *   <li>service    : authorization_code → access_token (POST) → userinfo (GET) → ExternalProfile</li>
 *   <li>mapper     : provider × providerUserId 매핑 조회·저장</li>
 * </ul>
 */
public interface OAuth2Service {

    /**
     * provider 콘솔에 등록된 client-id/secret 으로 환경 검증 — 미설정 시 false.
     * controller 가 false 시 사용자에게 "준비 중" 안내 후 redirect 하도록 사용.
     */
    boolean isProviderConfigured(OAuth2Provider provider);

    /** authorize URL 생성 — state, redirect_uri 포함. */
    String buildAuthorizeUrl(OAuth2Provider provider, String state, String redirectUri);

    /**
     * 콜백으로 받은 authorization code 를 token endpoint 에 교환 후 userinfo 호출 + 정규화.
     *
     * @return ExternalProfile (provider/providerUserId/email/name/nickname)
     * @throws OAuth2Exception token 교환 실패, userinfo 응답 파싱 실패, 필수 필드 누락 등
     */
    ExternalProfile exchangeAndFetchProfile(OAuth2Provider provider, String code, String redirectUri);
}
