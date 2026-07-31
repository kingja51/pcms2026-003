package com.gonet.config.filter;

import com.gonet.common.web.StaticResourcePaths;
import com.gonet.logging.access.service.AccessLogSnapshot;
import com.gonet.logging.access.service.AccessLogger;
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
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * 모든 HTTP 요청을 log_access 에 기록하는 PostFilter.
 *
 * <p>실행 순서: 거의 마지막 (LOWEST_PRECEDENCE - 50). 응답이 commit 된 후에 호출되어
 * status / response_ms 를 함께 기록. SiteContextInterceptor 의 site 결정도 끝난 시점.
 *
 * <p>제외 경로 (잡음 차단):
 * <ul>
 *   <li>정적 리소스: {@code /css/**}, {@code /js/**}, {@code /img/**}, {@code /tmpl/**},
 *       {@code /images/**}, {@code /fonts/**}, {@code /static/**}</li>
 *   <li>파비콘/robots: {@code /favicon.ico}, {@code /robots.txt}, {@code /sw.js}</li>
 *   <li>health/metrics: {@code /actuator/**}</li>
 *   <li>ERROR forward: {@code /error}</li>
 *   <li>확장자 .css/.js/.png/.jpg/.gif/.svg/.webp/.woff/.woff2/.ttf/.ico</li>
 * </ul>
 *
 * <p>실패 격리: AccessLogger 가 비동기 + try/catch 로 삼키므로 본 필터에서 추가 가드 불필요.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private final AccessLogger accessLogger;

    public AccessLogFilter(AccessLogger accessLogger) {
        this.accessLogger = accessLogger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                     HttpServletResponse res,
                                     FilterChain chain) throws ServletException, IOException {
        log.info("===AccessLogFilter starting for URI: {}", req.getRequestURI());
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            if (shouldLog(req)) {
                int elapsed = (int) (System.currentTimeMillis() - start);
                Long bytes = parseLong(res.getHeader("Content-Length"));
                // 스냅샷은 반드시 요청 스레드(=라이브 request)에서 동기 캡처해야 함.
                // 직접 req 를 async 로 넘기면 워커 시점엔 Tomcat 이 facade 를 recycle 한 상태라
                // IllegalStateException 이 매번 발생.
                AccessLogSnapshot snap = AccessLogSnapshot.capture(
                    req, res.getStatus(), elapsed, bytes);
                try {
                    accessLogger.log(snap);
                } catch (Exception ex) {
                    // accessLogger 자체가 try/catch 로 삼키므로 여기까지 올 일은 거의 없음
                    log.warn("ACCESS_LOG_DISPATCH_FAIL uri={} reason={}",
                        snap.requestUri(), ex.getMessage());
                }
            }
        }
    }

    private static boolean shouldLog(HttpServletRequest req) {
        // 정적/infra/루트 파일/정적 확장자 모두 공통 헬퍼에서 판정 — 중복 관리 회피.
        return !StaticResourcePaths.isStaticOrInfra(req.getRequestURI());
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException ex) { return null; }
    }
}
