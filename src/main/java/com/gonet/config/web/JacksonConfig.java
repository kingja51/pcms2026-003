package com.gonet.config.web;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * REST API JSON 응답의 {@code java.time.*} 직렬화/역직렬화 포맷 고정.
 *
 * <p>배경: Spring Boot 의 {@code spring.jackson.date-format} 은 {@code java.util.Date} 에만 적용되며
 * {@code LocalDateTime}/{@code LocalDate}/{@code LocalTime} 은 {@code JavaTimeModule} 이 처리.
 * 기본값은 ISO-8601 ("2026-04-24T14:10:41") — {@code yyyy-MM-dd HH:mm:ss} 로 통일하려면
 * {@link Jackson2ObjectMapperBuilderCustomizer} 로 커스텀 serializer/deserializer 를 등록해야 한다.
 *
 * <p>Thymeleaf 렌더링은 본 설정의 영향을 받지 않는다. 템플릿 내 {@code ${dto.createdAt}} 표현식은
 * {@code spring.mvc.format.date-time} (application.yml) 가 자동 등록하는 Spring MVC Formatter
 * 가 처리한다.
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN      = "yyyy-MM-dd";
    public static final String TIME_PATTERN      = "HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeFormat() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter df  = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter tf  = DateTimeFormatter.ofPattern(TIME_PATTERN);

        return builder -> builder
            .serializers(
                new LocalDateTimeSerializer(dtf),
                new LocalDateSerializer(df),
                new LocalTimeSerializer(tf))
            .deserializers(
                new LocalDateTimeDeserializer(dtf),
                new LocalDateDeserializer(df),
                new LocalTimeDeserializer(tf));
    }
}
