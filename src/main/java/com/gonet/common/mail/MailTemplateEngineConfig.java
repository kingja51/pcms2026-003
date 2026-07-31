package com.gonet.common.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * DB 기반 메일 템플릿 렌더링용 Thymeleaf 엔진 구성.
 *
 * <p>⚠️ 중요 — 본 빈은 반드시 {@link StringTemplateEngine} 래퍼 타입으로 노출한다.
 * {@link SpringTemplateEngine} 자체를 빈으로 등록하면 Spring Boot 의
 * {@code ThymeleafAutoConfiguration#templateEngine()} 의
 * {@code @ConditionalOnMissingBean} 조건이 만족되지 않아 기본 엔진 생성이 억제되고,
 * 대신 여기서 만든 StringTemplateResolver 엔진이 기본 ViewResolver 에 주입된다.
 * 결과적으로 뷰 이름(예: {@code "front/member-login"})이 템플릿 본문으로 간주돼
 * 그대로 응답에 출력된다.
 *
 * <p>내부 엔진은 {@link StringTemplateResolver} 만 부착 + {@code cacheable=false}
 * 로 관리자 편집이 즉시 반영되도록 한다.
 */
@Configuration
public class MailTemplateEngineConfig {

    @Bean
    public StringTemplateEngine mailStringTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return new StringTemplateEngine(engine);
    }
}
