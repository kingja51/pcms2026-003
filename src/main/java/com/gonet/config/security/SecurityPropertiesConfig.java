package com.gonet.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * [P1 임시 브리지] SecurityProperties 등록.
 *
 * <p>원본(gopcms2026)에서는 SecurityConfig 의 {@code @EnableConfigurationProperties}
 * 가 등록 주체였으나, SecurityConfig 는 인증 도메인(primary/system/login)과 함께
 * P2 에서 이식된다. 그 전까지 CspNonceFilter 등 P1 컴포넌트가 사용하는
 * SecurityProperties 를 본 클래스가 등록한다.
 *
 * <p>P2 에서 SecurityConfig 이식 후: 중복 등록은 무해하므로 유지해도 되고,
 * SecurityConfig 쪽 등록으로 일원화하며 본 클래스를 제거해도 된다.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityPropertiesConfig {
}
