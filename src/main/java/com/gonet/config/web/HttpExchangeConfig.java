package com.gonet.config.web;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpExchangeConfig {

    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        // 기본적으로 최근 100개의 요청을 메모리에 보관합니다.
        return new InMemoryHttpExchangeRepository();
    }
}
