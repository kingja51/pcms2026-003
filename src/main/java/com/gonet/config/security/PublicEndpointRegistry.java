package com.gonet.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link PublicEndpoint} 가 부착된 핸들러의 URL 패턴 레지스트리.
 *
 * <p>첫 요청 시 MVC 의 {@link RequestMappingHandlerMapping} 핸들러를 1회 스캔하여,
 * 컨트롤러 클래스 또는 메서드에 {@link PublicEndpoint} 가 있으면 그 URL 패턴을 모은다.
 * 이후 {@code DynamicAuthorizationManager} 가 DB 규칙 평가 전에 {@link #matches(HttpServletRequest)} 로
 * 공개 여부를 먼저 확인한다.
 *
 * <p><b>빈 이름 명시 조회</b>: actuator 의 {@code controllerEndpointHandlerMapping} 도
 * {@code RequestMappingHandlerMapping} 타입이라 by-type 주입은 다중 후보 충돌이 난다.
 * 표준 MVC 매핑 빈({@code "requestMappingHandlerMapping"}) 을 이름으로 명시 조회한다.
 *
 * <p>{@link ApplicationContext} 로 지연 조회하여 보안 필터 체인 ↔ MVC 매핑 초기화 순서 의존을 피한다 —
 * 매핑은 첫 HTTP 요청 시점엔 항상 완비되어 있으므로 lazy build 가 안전하다.
 */
@Component
public class PublicEndpointRegistry {

    private static final Logger log = LoggerFactory.getLogger(PublicEndpointRegistry.class);

    /** Spring MVC 표준 핸들러 매핑 빈 이름. */
    private static final String MVC_HANDLER_MAPPING_BEAN = "requestMappingHandlerMapping";

    private final ApplicationContext applicationContext;

    /** lazy build 결과 — 한 번 만들면 불변. */
    private volatile List<RequestMatcher> matchers;

    public PublicEndpointRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** 요청 URL 이 {@link PublicEndpoint} 컨트롤러의 패턴에 매칭되면 true. */
    public boolean matches(HttpServletRequest req) {
        List<RequestMatcher> ms = matchers;
        if (ms == null) {
            ms = build();
        }
        for (RequestMatcher m : ms) {
            if (m.matches(req)) {
                return true;
            }
        }
        return false;
    }

    private synchronized List<RequestMatcher> build() {
        if (matchers != null) {
            return matchers;
        }
        Set<String> patterns = new LinkedHashSet<>();
        RequestMappingHandlerMapping mapping = applicationContext.getBean(
            MVC_HANDLER_MAPPING_BEAN, RequestMappingHandlerMapping.class);
        mapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            if (isPublic(handlerMethod)) {
                // getPatternValues() 는 PathPattern / AntPath 전략 무관하게 패턴 문자열 반환
                patterns.addAll(info.getPatternValues());
            }
        });
        List<RequestMatcher> built = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            // method 미지정 = 모든 HTTP 메서드 매칭 (공개 = method 무관)
            built.add(PathPatternRequestMatcher.withDefaults().matcher(pattern));
        }
        log.info("[@PublicEndpoint] 공개 URL 패턴 {}건 등록: {}", patterns.size(), patterns);
        this.matchers = built;
        return built;
    }

    private boolean isPublic(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PublicEndpoint.class)
            || AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PublicEndpoint.class);
    }
}
