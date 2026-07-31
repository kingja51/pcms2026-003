package com.gonet.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * gopcms.security.* 바인딩.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.security")
public class SecurityProperties {

    private Login     login     = new Login();
    private RateLimit rateLimit = new RateLimit();
    private Csp       csp       = new Csp();
    private Tfa       tfa       = new Tfa();

    @Getter @Setter
    public static class Login {
        private int maxFailCount = 5;
        private int lockMinutes  = 30;
    }

    @Getter @Setter
    public static class RateLimit {
        /** 동일 IP 에서 로그인 시도 분당 허용 횟수. */
        private int loginPerIpPerMinute   = 10;
        /**
         * 동일 loginId 에 대한 로그인 시도 분당 허용 횟수 (IP 무관).
         * IP 분산 공격 시 IP 단일 키 제한이 우회되는 것을 막기 위한 2차 방어선.
         */
        private int loginPerUserPerMinute = 5;
        private int apiPerIpPerMinute     = 120;
    }

    @Getter @Setter
    public static class Csp {
        private boolean enabled = true;
        /**
         * Content-Security-Policy-Report-Only 동시 발급 여부 (기본 off).
         * 강제 정책에서 {@code style-src 'unsafe-inline'} 만 제거한 strict 변형을 함께 내보내
         * 위반을 {@code /csp-report} 로 수집한다 — 'unsafe-inline' 제거 가능 시점 측정용.
         *
         * <p><b>2026-05-31 결정</b>: script-src 가 nonce + strict-dynamic 으로 이미 잠겨 있어
         * style-src 의 {@code 'unsafe-inline'} 은 저위험으로 <b>유지</b>한다. 인라인 {@code style=} 속성
         * 348+건(+ Toast UI/지도 SDK 런타임 주입) 제거 비용이 과대하기 때문. 측정이 끝나 기본 off 로 둔다.
         * 재측정이 필요하면 true 로 켠다. (강제 정책의 {@code report-uri} 는 유지되어 실제 차단은 계속 수집)
         */
        private boolean reportOnly = false;
    }

    @Getter @Setter
    public static class Tfa {
        private String issuer = "GoPCMS";
    }
}
