package com.gonet.config.oauth2;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 회원 OAuth2 토큰/userinfo 호출 전용 {@link RestClient}.
 *
 * <p>이름 한정 — 다른 외부 호출과 충돌 방지. 주입 시 {@code @Qualifier("oauth2RestClient")}.
 * <p>baseUrl 미지정: provider 별 endpoint 가 다르므로 호출 시 절대 URL 사용.
 */
@Configuration
@EnableConfigurationProperties(OAuth2Properties.class)
public class OAuth2RestClientConfig {

    @Bean
    public RestClient oauth2RestClient(OAuth2Properties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}
