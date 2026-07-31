package com.gonet.config.interceptor;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.IpUtils;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * HTTP 요청 단위 감사로그 자동 포착 (CUD 전용).
 *
 * <p>관리자 영역({@code /admin/**}) 의 POST/PUT/PATCH/DELETE 요청을
 * {@link AuditLogger} 로 자동 팬아웃. GET 은 열람 로그로 별도 처리(S6 통계 파트).
 *
 * <p>예외/실패 시에도 {@code afterCompletion} 은 호출되므로
 * HTTP 상태코드 기반으로 {@code result} 결정:
 * <ul>
 *   <li>2xx/3xx → SUCCESS</li>
 *   <li>4xx     → FAIL (클라이언트 오류 — 권한/검증)</li>
 *   <li>5xx 또는 예외 → ERROR</li>
 * </ul>
 */
@Component
public class AuditHttpInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditHttpInterceptor.class);

    private static final Set<String> CUD_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditLogger auditLogger;

    public AuditHttpInterceptor(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        log.info("===AuditHttpInterceptor.afterCompletion executed for URI: {}", request.getRequestURI());
        String method = request.getMethod();
        if (!CUD_METHODS.contains(method)) return;

        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/admin/")) return;
        // 로그인/로그아웃은 LoginSuccess/FailureHandler 가 log_login 으로 별도 기록 — 중복 방지
        if (uri.equals("/login") || uri.equals("/logout")) return;

        String result = resolveResult(response.getStatus(), ex);
        String action = resolveAction(method, uri);
        String entity = resolveEntity(uri);

        AuditEvent event = AuditEvent.of(action, entity)
            .withHttp(method, uri,
                      IpUtils.clientIp(request),
                      request.getHeader("User-Agent"))
            .withResult(result)
            .withTraceId(MDC.get("traceId"));

        Authentication authn = SecurityContextHolder.getContext().getAuthentication();
        if (authn != null && authn.isAuthenticated()
                && authn.getPrincipal() instanceof CustomUserDetails u) {
            event.withActor(u.getUserId(), u.getUserType(), u.getUsername());
        }

        auditLogger.write(event);
    }

    private static String resolveResult(int status, Exception ex) {
        if (ex != null) return AuditEvent.of("", "").getResult().equals("") ? "ERROR" : "ERROR";
        if (status >= 500) return "ERROR";
        if (status >= 400) return "FAIL";
        return "SUCCESS";
    }

    private static String resolveAction(String method, String uri) {
        if ("DELETE".equals(method)) return "DELETE";
        // /admin/system/{entity}/{id} POST → UPDATE, /admin/system/{entity} POST → CREATE
        // 완벽한 판정은 불가 — 휴리스틱: 경로 세그먼트 개수로 추정
        int segments = uri.split("/").length;
        // /admin/system/site   → 4 seg(헤더 빈 문자열 포함) → CREATE
        // /admin/system/site/{id} → 5 seg → UPDATE
        return segments >= 5 ? "UPDATE" : "CREATE";
    }

    private static String resolveEntity(String uri) {
        // /admin/system/{entity}/... → {entity}
        String[] parts = uri.split("/");
        if (parts.length >= 4 && "admin".equals(parts[1]) && "system".equals(parts[2])) {
            return parts[3];
        }
        return "unknown";
    }
}
