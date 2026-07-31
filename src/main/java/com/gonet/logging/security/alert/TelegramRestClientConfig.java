package com.gonet.logging.security.alert;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 텔레그램 Bot API 전용 {@link RestClient} 빈.
 *
 * <p>이름 한정 — {@code @Qualifier("telegramRestClient")}. 다른 외부 호출 (날씨/OAuth2) 와 분리.
 *
 * <p>{@code gopcms.alert.telegram.enabled=true} 일 때만 빈 등록 — false 면 알림 자체 비활성.
 *
 * <p>주의: Lombok {@code @Qualifier copyableAnnotations} 패턴 (§0.47) 적용된 다른 RestClient 빈과 충돌 없음.
 * 본 빈은 자기 이름으로만 주입되므로 별도 마커 어노테이션 불필요.
 */
@Configuration
@EnableConfigurationProperties(TelegramAlertProperties.class)
@ConditionalOnProperty(prefix = "gopcms.alert.telegram", name = "enabled", havingValue = "true")
public class TelegramRestClientConfig {

    @Bean
    public RestClient telegramRestClient(TelegramAlertProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
