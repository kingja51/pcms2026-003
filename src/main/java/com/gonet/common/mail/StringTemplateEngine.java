package com.gonet.common.mail;

import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * DB 로부터 가져온 HTML 문자열을 Thymeleaf 로 렌더링하는 전용 래퍼.
 *
 * <p>{@link SpringTemplateEngine} 을 직접 빈으로 노출하면 Spring Boot 의
 * {@code @ConditionalOnMissingBean} 이 꺼져 기본 Thymeleaf ViewResolver 가
 * StringTemplateResolver 를 집어가고, 그 결과 일반 Thymeleaf 뷰 이름
 * (예: {@code "front/member-login"}) 이 "템플릿 본문 자체" 로 해석되어
 * 그대로 응답에 출력되는 문제가 발생한다.
 *
 * <p>본 클래스는 타입을 감싸서 오토컨픽 충돌을 방지하고,
 * {@link #process(String, Context)} 만 노출한다.
 */
public class StringTemplateEngine {

    private final SpringTemplateEngine delegate;

    public StringTemplateEngine(SpringTemplateEngine delegate) {
        this.delegate = delegate;
    }

    /** Thymeleaf HTML 문자열 렌더링. */
    public String process(String template, Context ctx) {
        if (template == null || template.isEmpty()) return "";
        return delegate.process(template, ctx);
    }
}
