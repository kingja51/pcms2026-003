package com.gonet.config.access;

import com.gonet.common.util.CsvUtils;
import com.gonet.common.util.JsonUtils;
import com.gonet.config.security.PublicEndpointRegistry;
import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityEventType;
import com.gonet.logging.security.service.SecurityLogger;
import com.gonet.primary.system.access.dto.RoleUrlAccessRule;
import com.gonet.primary.system.access.service.RoleUrlAccessService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * tb_role_url_access 기반 동적 URL 접근제어.
 *
 * <p>Spring Security 6.x {@link AuthorizationManager} 구현.
 * {@link SecurityConfig} 의 {@code authorizeHttpRequests().anyRequest().access(this)} 로 연결.
 *
 * <p>매칭 전략:
 * <ol>
 *   <li>{@link RoleUrlAccessService#getAllActive()} 에서 priority ASC 순 규칙 로드 (캐시)</li>
 *   <li>요청 method/URI 를 각 규칙과 순차 매칭 → 첫 매칭 규칙 적용</li>
 *   <li>매칭 없으면 DENY (fallback 규칙을 DB 에 반드시 등록 권장 — priority=9999)</li>
 * </ol>
 *
 * <p>{@code access_type} 별 판정:
 * <ul>
 *   <li>PERMIT_ALL     : 무조건 통과</li>
 *   <li>ANONYMOUS      : 비로그인만</li>
 *   <li>AUTHENTICATED  : 로그인만 (+ allowed_user_types 추가 필터)</li>
 *   <li>ROLE           : user.roleIds ∩ rule.requiredRoles (UUID) ≠ ∅</li>
 *   <li>AUTH           : 세밀권한 일치 (S1 확장 — 현재는 AUTHENTICATED 와 동일)</li>
 *   <li>IP_ONLY        : CIDR 매칭 (S1 확장 — 현재는 DENY)</li>
 *   <li>DENY           : 무조건 거부</li>
 * </ul>
 */
@Component
public class DynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger log = LoggerFactory.getLogger(DynamicAuthorizationManager.class);

    private final RoleUrlAccessService    service;
    private final SecurityLogger          securityLogger;
    private final PublicEndpointRegistry  publicEndpointRegistry;

    public DynamicAuthorizationManager(RoleUrlAccessService service,
                                        SecurityLogger securityLogger,
                                        PublicEndpointRegistry publicEndpointRegistry) {
        this.service = service;
        this.securityLogger = securityLogger;
        this.publicEndpointRegistry = publicEndpointRegistry;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authSupplier,
                                        RequestAuthorizationContext ctx) {
        HttpServletRequest req = ctx.getRequest();

        // @PublicEndpoint 컨트롤러 — DB 규칙 평가 전에 먼저 GRANT.
        // tb_role_url_access 등록 없이 공개. 미부착 컨트롤러는 아래 DB 규칙 + fallback DENY 로 보호.
        if (publicEndpointRegistry.matches(req)) {
            log.info("===[DynamicAuth] @PublicEndpoint 매칭 -> GRANT: {} {}", req.getMethod(), req.getRequestURI());
            return new AuthorizationDecision(true);
        }

        List<RoleUrlAccessRule> rules = service.getAllActive();

        RoleUrlAccessRule matched = findMatching(rules, req);
        if (matched == null) {
            log.warn("No role_url_access rule matched: {} {} — DENY", req.getMethod(), req.getRequestURI());
            return new AuthorizationDecision(false);
        }

        boolean granted = evaluate(matched, authSupplier, req);
        if (!granted) {
            if (log.isDebugEnabled()) {
                log.info("Access DENIED by rule priority={} pattern={} type={}",
                    matched.getPriority(), matched.getUrlPattern(), matched.getAccessType());
            }
            // §0.48 후속 — PRIVILEGE_ESCALATION (severity=CRITICAL)
            // 인증된 사용자가 자기 권한 외 ROLE/AUTH/AUTHENTICATED 보호 리소스 접근 시도 시만 적재.
            // 비로그인 anonymous 거부 / DENY 룰은 폭주 우려로 적재 안 함 (별도 dedup 도입 후 확장).
            try {
                Authentication auth = authSupplier.get();
                boolean isAuthenticated = auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal());
                String type = matched.getAccessType();
                boolean meaningful = isAuthenticated
                    && (RoleUrlAccessRule.ROLE.equals(type)
                        || RoleUrlAccessRule.AUTH.equals(type)
                        || RoleUrlAccessRule.AUTHENTICATED.equals(type));
                if (meaningful) {
                    String userId = (auth.getPrincipal() instanceof CustomUserDetails ud) ? ud.getUserId() : null;
                    String userType = (auth.getPrincipal() instanceof CustomUserDetails ud) ? ud.getUserType() : null;
                    securityLogger.write(SecurityEvent.of(SecurityEventType.PRIVILEGE_ESCALATION)
                        .withActor(userId)
                        .withDetail("{\"matchedPattern\":" + JsonUtils.quote(matched.getUrlPattern())
                            + ",\"accessType\":" + JsonUtils.quote(type)
                            + ",\"requiredRoles\":" + JsonUtils.quote(matched.getRequiredRoles())
                            + ",\"allowedUserTypes\":" + JsonUtils.quote(matched.getAllowedUserTypes())
                            + ",\"userType\":" + JsonUtils.quote(userType) + "}"));
                }
            } catch (Exception ignored) {
                // fallback logger 가 흡수 — 인가 평가 흐름은 그대로
            }
        }
        return new AuthorizationDecision(granted);
    }

    // ------------------------------------------------------------------
    // 매칭
    // ------------------------------------------------------------------
    private RoleUrlAccessRule findMatching(List<RoleUrlAccessRule> rules, HttpServletRequest req) {
        String method = req.getMethod();
        for (RoleUrlAccessRule r : rules) {
            String pattern = r.getUrlPattern();
            if (pattern == null || pattern.isBlank()) {
                log.warn("Skip role_url_access row with empty url_pattern: id={} priority={}",
                    r.getRoleUrlAccessId(), r.getPriority());
                continue;
            }
            if (!methodMatches(r.getHttpMethod(), method)) continue;
            // Spring Security 6.5 — PathPatternRequestMatcher 로 교체 (AntPathRequestMatcher deprecated).
            // method=null 은 모든 HTTP 메서드 매칭. methodMatches() 가 사전 필터하므로 path 만 검사하면 됨.
            RequestMatcher matcher = r.isMethodAll()
                ? PathPatternRequestMatcher.withDefaults().matcher(pattern)
                : PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.valueOf(r.getHttpMethod()), pattern);
            if (matcher.matches(req)) {
                log.info("===[DynamicAuth] 매칭된 규칙: priority={}, pattern={}, method={}, accessType={}", r.getPriority(), r.getUrlPattern(), r.getHttpMethod(), r.getAccessType());
                return r;
            }
        }
        return null;
    }

    private boolean methodMatches(String ruleMethod, String requestMethod) {
        if (ruleMethod == null || "ALL".equalsIgnoreCase(ruleMethod)) return true;
        try {
            HttpMethod.valueOf(ruleMethod.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return ruleMethod.equalsIgnoreCase(requestMethod);
    }

    // ------------------------------------------------------------------
    // 판정
    // ------------------------------------------------------------------
    private boolean evaluate(RoleUrlAccessRule rule,
                              Supplier<Authentication> authSupplier,
                              HttpServletRequest req) {
        String type = rule.getAccessType();
        Authentication auth = authSupplier.get();
        boolean isAuthenticated = auth != null && auth.isAuthenticated()
            && !"anonymousUser".equals(auth.getPrincipal());

        log.info("===[DynamicAuth] 평가 시작: accessType={}, 로그인여부={}", type, isAuthenticated);

        switch (type) {
            case RoleUrlAccessRule.PERMIT_ALL:
                log.info("===[DynamicAuth] 평가 결과: PERMIT_ALL -> 통과");
                return true;
            case RoleUrlAccessRule.DENY:
                log.info("===[DynamicAuth] 평가 결과: DENY -> 거부");
                return false;
            case RoleUrlAccessRule.ANONYMOUS:
                log.info("===[DynamicAuth] 평가 결과: ANONYMOUS -> {}", !isAuthenticated);
                return !isAuthenticated;
            case RoleUrlAccessRule.AUTHENTICATED:
                boolean authAllowed = isAuthenticated && userTypeAllowed(rule, auth);
                log.info("===[DynamicAuth] 평가 결과: AUTHENTICATED -> {}", authAllowed);
                return authAllowed;
            case RoleUrlAccessRule.ROLE:
                boolean roleAllowed = isAuthenticated
                    && userTypeAllowed(rule, auth)
                    && hasAnyRequiredRole(rule, auth);
                log.info("===[DynamicAuth] 평가 결과: ROLE -> {}", roleAllowed);
                return roleAllowed;
            case RoleUrlAccessRule.AUTH:
                // S1: tb_role_auth 세밀권한 매칭 (현재는 인증만 요구)
                boolean authTypeAllowed = isAuthenticated && userTypeAllowed(rule, auth);
                log.info("===[DynamicAuth] 평가 결과: AUTH -> {}", authTypeAllowed);
                return authTypeAllowed;
            case RoleUrlAccessRule.IP_ONLY:
                // S1: CIDR 매칭 — 현재는 conservative DENY
                log.warn("IP_ONLY rule not yet implemented: {}", rule.getUrlPattern());
                return false;
            default:
                log.warn("Unknown access_type '{}' for rule {}", type, rule.getUrlPattern());
                return false;
        }
    }

    /**
     * allowed_user_types CSV 매칭 — user_type 외에 role_codes 도 함께 비교.
     *
     * <p><b>DDL 작성 표준 (옵션 A — Spring {@code hasRole()} 스타일)</b>:
     * {@code allowed_user_types} 에는 {@code ROLE_} 접두사 없이 적는다.
     * <pre>
     *   ✅ 권장:    'STAFF,EMPLOYEE,ADMIN'
     *   ⚠️ 허용:    'ROLE_STAFF,ROLE_EMPLOYEE,ROLE_ADMIN'   (접두사 자동 제거 비교)
     *   ⚠️ 허용:    'STAFF,ROLE_ADMIN'                       (섞어 작성도 동작)
     * </pre>
     * DB 의 {@code v_user_login.role_codes} 는 Spring Security 표준에 따라
     * {@code ROLE_ADMIN} 형태로 저장 — 본 메서드가 비교 시 양쪽 모두에서 {@code ROLE_}
     * 접두사를 제거({@link #stripRolePrefix})하여 정규화 매칭.
     *
     * <p>매칭 알고리즘:
     * <ol>
     *   <li>{@code allowedSet}  = DDL CSV → 정규화 (ROLE_ 제거)</li>
     *   <li>{@code userRoleSet} = {@code v_user_login.role_codes} → 정규화</li>
     *   <li>{@code userTypeHit} = {@code allowedSet} 에 {@code ud.userType} 정규화 형태가 포함</li>
     *   <li>{@code roleCodeHit} = {@code allowedSet} ∩ {@code userRoleSet} ≠ ∅</li>
     *   <li>결과 = {@code userTypeHit || roleCodeHit}</li>
     * </ol>
     *
     * <p>예: 사용자 userType=STAFF, roleCodes="ROLE_ADMIN,ROLE_STAFF,ROLL_MANAGER".
     *  · allowed='ADMIN'                → ROLE_ADMIN 정규화→ADMIN 매칭 → ALLOW
     *  · allowed='STAFF,MEMBER'         → userType STAFF 일치 → ALLOW
     *  · allowed='ROLL_MANAGER'         → roleCodes 안에 일치 → ALLOW (역할 코드 기반 fine grain)
     *  · allowed='USER'                 → 어디에도 없음 → DENY
     */
    private boolean userTypeAllowed(RoleUrlAccessRule rule, Authentication auth) {
        String allowed = rule.getAllowedUserTypes();
        if (allowed == null || allowed.isBlank()) return true;
        if (!(auth.getPrincipal() instanceof CustomUserDetails ud)) return false;

        // ROLE_ 접두사 정규화 — 양쪽 (DDL allowed / 사용자 roleCodes) 모두 제거 후 비교
        Set<String> allowedNorm = normalizeRoleSet(CsvUtils.toSet(allowed));

        boolean userTypeHit = allowedNorm.contains(stripRolePrefix(ud.getUserType()));

        Set<String> userRoleNorm = normalizeRoleSet(CsvUtils.toSet(ud.getRoleCodes()));
        boolean roleCodeHit = !java.util.Collections.disjoint(allowedNorm, userRoleNorm);

        boolean result = userTypeHit || roleCodeHit;
        log.info("===[DynamicAuth] userType/roleCodes 검사: 필요={} (정규화={}), userType={}, roleCodes={} (정규화={}) -> userTypeHit={} / roleCodeHit={} / 최종={}",
            allowed, allowedNorm, ud.getUserType(), ud.getRoleCodes(), userRoleNorm, userTypeHit, roleCodeHit, result);
        return result;
    }

    /** {@code ROLE_ADMIN} → {@code ADMIN}. null/빈 문자열은 빈 문자열 반환. */
    private static String stripRolePrefix(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.startsWith("ROLE_") ? s.substring(5) : s;
    }

    /** Set 의 모든 원소에 {@link #stripRolePrefix} 적용. */
    private static Set<String> normalizeRoleSet(Set<String> src) {
        if (src == null || src.isEmpty()) return java.util.Collections.emptySet();
        Set<String> out = new java.util.LinkedHashSet<>(src.size());
        for (String s : src) out.add(stripRolePrefix(s));
        return out;
    }

    private boolean hasAnyRequiredRole(RoleUrlAccessRule rule, Authentication auth) {
        if (!(auth.getPrincipal() instanceof CustomUserDetails ud)) return false;
        boolean result = CsvUtils.hasAnyMatch(ud.getRoleIds(), rule.getRequiredRoles());
        log.info("===[DynamicAuth] Role 검사: 필요={}, 현재={} -> {}", rule.getRequiredRoles(), ud.getRoleIds(), result);
        return result;
    }
}
