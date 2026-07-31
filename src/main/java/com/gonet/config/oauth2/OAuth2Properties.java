package com.gonet.config.oauth2;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원 OAuth2 소셜 로그인 설정 — application.yml {@code gopcms.oauth2.*}.
 *
 * <p>provider 별 client-id/secret 외부화. token/userinfo URL 은 코드 상수로 고정 (provider 변경 거의 없음).
 *
 * <p>redirect-base-url 은 콜백 URI 도출의 origin (예: {@code http://127.0.0.1}).
 * 운영 도메인 변경 시 본 값만 교체 — provider 콘솔 등록 URI 와 정확히 일치해야 함.
 */
@Getter
@Setter
@ConfigurationProperties("gopcms.oauth2")
public class OAuth2Properties {

    /** OAuth2 기능 전역 토글 — false 면 컨트롤러 진입 차단. */
    private boolean enabled = true;

    /** redirect_uri origin — {@code http://127.0.0.1:8080} 형태. 콜백 host 검증에도 사용. */
    private String redirectBaseUrl;

    /** state 세션 보관 키 prefix. */
    private String stateSessionKey = "PCMS_OAUTH2_STATE";

    /** 외부 프로필 세션 보관 키 (가입 폼 prefill 용). */
    private String externalProfileSessionKey = "PCMS_OAUTH2_EXTERNAL";

    /** 연결/응답 타임아웃 (ms). */
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs    = 10_000;

    private Naver  naver  = new Naver();
    private Kakao  kakao  = new Kakao();
    private Google google = new Google();

    /**
     * provider 별 명시 callback URL — provider 콘솔에 등록한 정확한 URL 을 그대로 보관.
     * 비어 있으면 {@code redirectBaseUrl + /member/oauth2/{provider}/callback} 으로 자동 도출.
     */
    @Getter @Setter
    public static class Naver {
        private boolean enabled  = true;
        private String  clientId;
        private String  clientSecret;
        private String  callbackUrl;
    }

    @Getter @Setter
    public static class Kakao {
        private boolean enabled  = true;
        private String  clientId;
        private String  clientSecret;       // optional — 카카오는 secret 미사용 가능
        private String  callbackUrl;
    }

    @Getter @Setter
    public static class Google {
        private boolean enabled  = true;
        private String  clientId;
        private String  clientSecret;
        private String  callbackUrl;
    }
}
