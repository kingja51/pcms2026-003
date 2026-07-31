package com.gonet.config.access;

import com.gonet.logging.error.service.ErrorLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * 모든 미처리 예외를 가로채서 log_error 에 기록한 뒤,
 * 다른 {@link HandlerExceptionResolver} 가 정상적으로 응답을 만들도록 위임한다.
 *
 * <p>핵심:
 * <ul>
 *   <li>{@link Ordered#HIGHEST_PRECEDENCE} — 다른 resolver 보다 먼저 실행</li>
 *   <li>{@code resolveException()} 이 INSERT 후 {@code null} 반환 → resolver 체인 다음 단계로 넘어감</li>
 *   <li>이 자체는 응답을 만들지 않으므로 사용자에게 보이는 에러 페이지는 기존 `error/4xx.html` 등이 그대로 처리</li>
 * </ul>
 *
 * <p>스킵 정책 — 노이즈 필터링:
 * <ul>
 *   <li>{@link AccessDeniedException} 403 — 일상적이라 too noisy</li>
 *   <li>{@link AuthenticationException} 401 — 동일</li>
 *   <li>{@link InsufficientAuthenticationException} — 동일</li>
 *   <li>{@code org.apache.catalina.connector.ClientAbortException} — 사용자가 페이지 떠남</li>
 *   <li>{@code org.springframework.web.servlet.resource.NoResourceFoundException} — 정적 리소스 404
 *       (Spring 6+; 예: 브라우저의 stale ServiceWorker 가 {@code /sw.js} 를 계속 요청)</li>
 *   <li>{@code org.springframework.web.servlet.NoHandlerFoundException} — 매핑 없는 URL 404</li>
 *   <li>{@code HttpRequestMethodNotSupportedException} — 405 메서드 불일치</li>
 *   <li>{@code HttpMediaTypeNotAcceptableException} — 406 협상 실패</li>
 * </ul>
 *
 * <p>모두 4xx 클라이언트 측 이슈라 운영 알람 대상이 아님. 필요하면 별도 분석은 log_access 의
 * status_code 4xx 필터로 충분.
 *
 * <p>Filter 단계 예외 (예: SecurityFilter) 는 잡지 못함 — 그건 별도 Filter 로 보강 가능.
 * 본 resolver 는 컨트롤러/서비스/매퍼 단계의 예외만 처리.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorLoggingExceptionResolver implements HandlerExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(ErrorLoggingExceptionResolver.class);

    private final ErrorLogger errorLogger;

    public ErrorLoggingExceptionResolver(ErrorLogger errorLogger) {
        this.errorLogger = errorLogger;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest req,
                                          HttpServletResponse res,
                                          Object handler,
                                          Exception ex) {
        if (shouldSkip(ex)) return null;
        try {
            // 응답 status 가 아직 정해지지 않았을 가능성이 높으므로 500 가정
            errorLogger.log(req, ex, 500);
        } catch (Exception logEx) {
            log.warn("ERROR_LOG_DISPATCH_FAIL uri={} reason={}",
                req.getRequestURI(), logEx.getMessage());
        }
        // null 반환 → 다른 resolver(예: DefaultHandlerExceptionResolver, ResponseStatusExceptionResolver,
        // @ControllerAdvice) 가 응답을 만들도록 위임
        return null;
    }

    /** 4xx 클라이언트 측 노이즈 — 서버 알람 대상 아님 */
    private static final java.util.Set<String> SKIP_FQCNS = java.util.Set.of(
        // 사용자가 페이지 떠남
        "org.apache.catalina.connector.ClientAbortException",
        // 정적 리소스 404 (Spring 6+) — stale ServiceWorker(/sw.js) 등
        "org.springframework.web.servlet.resource.NoResourceFoundException",
        // 매핑 없는 URL 404
        "org.springframework.web.servlet.NoHandlerFoundException",
        // 405 메서드 불일치
        "org.springframework.web.HttpRequestMethodNotSupportedException",
        // 406 협상 실패
        "org.springframework.web.HttpMediaTypeNotAcceptableException",
        // 415 미지원 미디어 타입
        "org.springframework.web.HttpMediaTypeNotSupportedException"
    );

    /** 노이즈 예외 — 보안/네트워크/4xx 관련은 log_error 가 아닌 log_access 도메인으로 분석 */
    private static boolean shouldSkip(Exception ex) {
        if (ex == null) return true;
        if (ex instanceof AccessDeniedException) return true;
        if (ex instanceof AuthenticationException) return true;
        if (ex instanceof InsufficientAuthenticationException) return true;
        return SKIP_FQCNS.contains(ex.getClass().getName());
    }
}
