package com.gonet.logging.access.service;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.util.IpUtils;
import com.gonet.common.util.SensitiveParamMasker;
import com.gonet.config.filter.AccessLogFilter;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * 액세스 로그 적재용 요청 스냅샷.
 *
 * <p>이 record 는 {@link AccessLogFilter} 가 요청 스레드(=라이브 request 객체)에서
 * 동기 캡처한 후, {@link AccessLogger#log(AccessLogSnapshot)} 의 {@code @Async}
 * dispatch 에 그대로 전달된다. 비동기 워커는 본 record 만 읽고 원본 {@code HttpServletRequest}
 * 에는 더 이상 접근하지 않으므로 Tomcat 의 request facade recycle 과 무관하게 안전.
 *
 * <p>actor 캡처 정책 (2026-04-30 수정):
 * <ul>
 *   <li>{@code AccessLogFilter} 는 Spring Security 필터 체인 <b>밖</b>에 위치한다
 *       — {@code chain.doFilter} 가 반환되는 시점엔 {@code SecurityContextHolderFilter}
 *       가 이미 {@link SecurityContextHolder#clearContext()} 를 실행한 뒤이므로
 *       {@code SecurityContextHolder} 는 비어 있다.</li>
 *   <li>따라서 (1) 우선 {@code SecurityContextHolder} 를 시도하고
 *       (혹시 어떤 컨텍스트에서 살아있을 수 있으니), (2) 비어 있으면
 *       {@code HttpSession} 의 {@value HttpSessionSecurityContextRepository#SPRING_SECURITY_CONTEXT_KEY}
 *       속성을 읽어 actor 를 복원한다. 폼 로그인(stateful) 환경에서는
 *       세션 속성이 응답 후에도 그대로 남아 있어 안전.</li>
 *   <li>관련 결함: 직전까지는 actor 캡처를 워커
 *       에서 수행 → 워커 dispatch 시점에도 SC 가 이미 비어 있어 모든 행이
 *       {@code actor_user_type=ANONYMOUS} 로 기록되던 회귀 (2026-04-30).</li>
 * </ul>
 */
public record AccessLogSnapshot(
    String requestUri,
    String httpMethod,
    String queryString,
    String referer,
    String clientIp,
    String userAgent,
    String sessionIdTail,
    String traceId,
    String siteId,
    String siteCode,
    String menuId,
    int    statusCode,
    int    responseMs,
    Long   bytesSent,
    String actorUserId,
    String actorUserType,
    String actorLoginId
) {

    /** Request 가 살아있는 동안에만 호출. async 워커에서 재호출하면 IllegalStateException. */
    public static AccessLogSnapshot capture(HttpServletRequest req,
                                             int statusCode,
                                             int responseMs,
                                             Long bytesSent) {
        Authentication auth = resolveAuthentication(req);
        return new AccessLogSnapshot(
            trim(extractUri(req)),
            req.getMethod(),
            trim(SensitiveParamMasker.mask(req.getQueryString())),
            trim(req.getHeader("Referer")),
            IpUtils.clientIp(req),
            trim(req.getHeader("User-Agent")),
            extractSessionIdTail(req),
            extractTraceId(),
            req.getAttribute("siteId") == null ? req.getParameter("siteId") : req.getAttribute("siteId").toString(),
            extractSiteCode(req),
            extractMenuId(req),
            statusCode,
            responseMs,
            bytesSent,
            extractActorUserId(auth),
            extractActorUserType(auth),
            extractActorLoginId(auth)
        );
    }

    private static String extractUri(HttpServletRequest req) {
        if (req == null) return null;
        return req.getRequestURI();
    }

    /**
     * siteCode 추출 — 우선순위: request attribute → request parameter → URI 첫 세그먼트.
     *
     * <p>URI 첫 세그먼트 폴백은 {@code "/"} 같은 빈 path 에서 split 결과가
     * {@code [""]} 또는 {@code []} 가 되어 {@code [1]} 접근 시 ArrayIndexOutOfBoundsException
     * 이 발생하던 회귀 (2026-04-30 root 접근 5xx) 를 방어한다.
     */
    private static String extractSiteCode(HttpServletRequest req) {
        Object attr = req.getAttribute("siteCode");
        if (attr != null) return attr.toString();
        String param = req.getParameter("siteCode");
        if (param != null && !param.isBlank()) return param;
        String uri = req.getRequestURI();
        if (uri == null || uri.isEmpty() || "/".equals(uri)) return null;
        String[] parts = uri.split("/");
        for (String p : parts) {
            if (p != null && !p.isEmpty()) return p;
        }
        return null;
    }

    private static String extractMenuId(HttpServletRequest req) {
        String menuId = null;

        // 1. Attribute 확인
        Object attr = req.getAttribute("menuId");
        if (attr != null) {
            menuId = attr.toString();
        }
        // 2. Parameter 확인
        else {
            String param = req.getParameter("menuId");
            if (param != null && !param.isBlank()) {
                menuId = param;
            }
            // 3. URI Fallback (기존 split 로직 제거, 전체 URI 사용)
            else {
                String uri = req.getRequestURI();
                if (uri != null && !uri.isBlank() && !"/".equals(uri)) {
                    menuId = uri;
                }
            }
        }

        // DB varchar(36) 제약조건에 맞춰 최종 길이를 최대 36자로 안전하게 Substring
        if (menuId != null && menuId.length() > 36) {
            return menuId.substring(0, 36);
        }

        return menuId;
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static String extractSessionIdTail(HttpServletRequest req) {
        try {
            String sid = (req.getSession(false) == null) ? null : req.getSession(false).getId();
            if (sid == null || sid.length() < 8) return sid;
            return "..." + sid.substring(sid.length() - 8);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String extractTraceId() {
        // micrometer tracing 은 'traceId', 일부 logback 설정은 'X-B3-TraceId'
        String t = MDC.get("traceId");
        return (t != null) ? t : MDC.get("X-B3-TraceId");
    }

    /**
     * 1차: SecurityContextHolder (대부분 비어 있음 — Security 체인 밖에서 호출되므로)<br/>
     * 2차: HttpSession 의 SPRING_SECURITY_CONTEXT 속성 — stateful 폼 로그인 환경에서
     * 응답 후에도 남아 있는 정식 저장소.
     */
    private static Authentication resolveAuthentication(HttpServletRequest req) {
        Authentication holder = SecurityContextHolder.getContext().getAuthentication();
        if (isAuthenticated(holder)) return holder;
        try {
            HttpSession session = req.getSession(false);
            if (session == null) return holder;
            Object stored = session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (stored instanceof SecurityContext sc) {
                Authentication fromSession = sc.getAuthentication();
                if (isAuthenticated(fromSession)) return fromSession;
            }
        } catch (Exception ignore) {
            // 세션 무효화 등 — actor 미상으로 폴백
        }
        return holder;
    }

    private static boolean isAuthenticated(Authentication auth) {
        return auth != null
            && auth.isAuthenticated()
            && !"anonymousUser".equals(auth.getPrincipal());
    }

    private static String extractActorUserId(Authentication auth) {
        if (!isAuthenticated(auth)) return null;
        if (auth.getPrincipal() instanceof CustomUserDetails ud) {
            String uid = ud.getUserId();
            if (uid != null && !uid.isBlank()) return uid;
        }
        return auth.getName();
    }

    private static String extractActorUserType(Authentication auth) {
        if (!isAuthenticated(auth)) return AuditContext.ANONYMOUS;
        if (auth.getPrincipal() instanceof CustomUserDetails ud) {
            String t = ud.getUserType();
            if (t != null && !t.isBlank()) return t;
        }
        return null;
    }

    private static String extractActorLoginId(Authentication auth) {
        if (!isAuthenticated(auth)) return null;
        if (auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getUsername();
        }
        return auth.getName();
    }

}
