package com.gonet.primary.member.oauth2.dto;

import java.util.Locale;

/**
 * 회원 OAuth2 외부 제공자 — provider 별 endpoint·scope·label 상수 묶음.
 *
 * <p>endpoint URL 은 운영 정책상 자주 변경되지 않으므로 코드 상수로 고정.
 * client-id/secret 만 {@link com.gonet.config.oauth2.OAuth2Properties} 에서 외부화.
 *
 * <p>provider 추가 가이드:
 * <ol>
 *   <li>enum 항목 + endpoint URL/scope 상수 추가</li>
 *   <li>OAuth2Properties 에 client-id/secret 필드 추가 + isEnabled() 분기</li>
 *   <li>OAuth2Service.fetchProfile() 의 응답 파싱 분기 추가 (provider 별 JSON 구조 다름)</li>
 *   <li>tb_member_oauth.chk_oauth_provider CHECK 제약 갱신 (DDL)</li>
 * </ol>
 */
public enum OAuth2Provider {

    NAVER(
        "네이버",
        "https://nid.naver.com/oauth2.0/authorize",
        "https://nid.naver.com/oauth2.0/token",
        "https://openapi.naver.com/v1/nid/me",
        "name email"
    ),
    KAKAO(
        "카카오",
        "https://kauth.kakao.com/oauth/authorize",
        "https://kauth.kakao.com/oauth/token",
        "https://kapi.kakao.com/v2/user/me",
        "profile_nickname"
    ),
    GOOGLE(
        "Google",
        "https://accounts.google.com/o/oauth2/v2/auth",
        "https://oauth2.googleapis.com/token",
        "https://www.googleapis.com/oauth2/v3/userinfo",
        "openid email profile"
    );

    private final String label;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String userinfoUrl;
    private final String defaultScope;

    OAuth2Provider(String label, String authorizeUrl, String tokenUrl,
                   String userinfoUrl, String defaultScope) {
        this.label = label;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.userinfoUrl = userinfoUrl;
        this.defaultScope = defaultScope;
    }

    public String label()        { return label; }
    public String authorizeUrl() { return authorizeUrl; }
    public String tokenUrl()     { return tokenUrl; }
    public String userinfoUrl()  { return userinfoUrl; }
    public String defaultScope() { return defaultScope; }

    /** URL path variable 의 provider 값을 안전하게 enum 으로 변환 (대소문자 무관). */
    public static OAuth2Provider fromCode(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
