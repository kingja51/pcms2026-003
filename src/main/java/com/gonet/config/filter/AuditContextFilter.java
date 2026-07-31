package com.gonet.config.filter;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.util.IpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청별 {@link AuditContext} 주입 필터.
 *
 * <p>본 필터는 <b>IP 만</b> ThreadLocal 에 세팅한다. Spring Security 보다 앞에 실행되어야
 * Security 체인 안쪽(LoginSuccess/Failure Handler 등)에서도
 * {@code AuditContext.ip()} 가 정상 동작한다.
 *
 * <p>{@code userId} 는 필터에서 캡처하지 않고 {@link AuditContext#userId()} 가
 * 호출 시점에 {@code SecurityContextHolder} 를 직접 조회한다 —
 * Security 인증 이전/이후 어느 시점에 호출돼도 항상 최신 값이 반환된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class AuditContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        log.info("===AuditContextFilter setting IP for URI: {}", req.getRequestURI());
        try {
            AuditContext.setIp(IpUtils.clientIp(req));
            chain.doFilter(req, res);
        } finally {
            AuditContext.clear();
        }
    }
}
