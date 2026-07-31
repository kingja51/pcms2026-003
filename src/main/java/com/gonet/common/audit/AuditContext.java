package com.gonet.common.audit;

import com.gonet.primary.system.login.dto.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 요청 단위 감사 정보.
 *
 * <p>설계:
 * <ul>
 *   <li>{@code ip} 는 {@link com.gonet.config.filter.AuditContextFilter} 가 요청 초기에 ThreadLocal 에 세팅,
 *       응답 직전 clear.</li>
 *   <li>{@code userId} 는 ThreadLocal 에 담지 않고 호출 시점에
 *       {@link SecurityContextHolder} 를 lazy 조회한다. LoginSuccessHandler 처럼
 *       인증이 방금 세팅된 코드에서도, 일반 컨트롤러/서비스/매퍼 흐름에서도
 *       항상 현재 auth 를 반영한다.</li>
 * </ul>
 *
 * <p>폴백:
 * <ul>
 *   <li>userId : {@code ANONYMOUS} (비인증) / {@code SYSTEM} (HTTP 요청 밖 — 스케줄러 등)</li>
 *   <li>ip     : {@code 0.0.0.0}</li>
 * </ul>
 *
 * <p>{@code AuditInterceptor} 가 INSERT/UPDATE 파라미터의
 * {@code created_by}/{@code updated_by} 등 6 감사컬럼에 자동 주입한다.
 */
public final class AuditContext {

    public static final String SYSTEM_USER = "SYSTEM";
    public static final String ANONYMOUS   = "ANONYMOUS";
    public static final String UNKNOWN_IP  = "0.0.0.0";

    private static final ThreadLocal<String> IP            = new ThreadLocal<>();
    private static final ThreadLocal<String> FORCED_USER   = new ThreadLocal<>();

    private AuditContext() {}

    /** 필터 진입 시 IP 세팅. */
    public static void setIp(String ip) {
        IP.set(ip);
    }

    /**
     * 비로그인 본인 작업(예: 비번 찾기) 처럼 SecurityContext 가 비어 있으나
     * actor 가 회원 본인의 member_id 로 식별 가능한 경우, 호출 측이 짧게 actor 를
     * override 한다. 반드시 try/finally 로 {@link #clearForcedUserId()} 호출.
     */
    public static void forceUserId(String userId) {
        FORCED_USER.set(userId);
    }

    public static void clearForcedUserId() {
        FORCED_USER.remove();
    }

    /**
     * 현재 요청의 사용자 식별자.
     *
     * <p>우선순위: CustomUserDetails.userId(UUID v7 PK) → Authentication.getName() → ANONYMOUS → SYSTEM.
     *
     * <p>HTTP 요청 밖(스케줄러/seed) 에서는 SecurityContextHolder 가 비어 있어 SYSTEM 반환.
     */
    public static String userId() {
        String forced = FORCED_USER.get();
        if (forced != null && !forced.isBlank()) {
            return forced;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            // HTTP 요청 밖: 스케줄러, 부팅 seed 등
            return SYSTEM_USER;
        }
        if (!auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ANONYMOUS;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails ud) {
            String uid = ud.getUserId();
            if (uid != null && !uid.isBlank()) return uid;
        }
        String name = auth.getName();
        return (name == null || name.isBlank()) ? ANONYMOUS : name;
    }

    public static String ip() {
        String v = IP.get();
        return (v == null || v.isBlank()) ? UNKNOWN_IP : v;
    }

    public static void clear() {
        IP.remove();
        FORCED_USER.remove();
    }
}
