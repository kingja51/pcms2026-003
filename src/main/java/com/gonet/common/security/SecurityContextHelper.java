package com.gonet.common.security;

import com.gonet.primary.system.login.dto.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext 조회 헬퍼 — 도메인 코드에서 현재 사용자/부서/권한을 1줄로 추출.
 *
 * <p>HTTP 요청 밖(스케줄러 등)에서는 모두 null 또는 false 반환.
 *
 * <p>{@link com.gonet.common.audit.AuditContext} 는 감사 컬럼(created_by/ip) 용도 — 별도.
 */
public final class SecurityContextHelper {

    private SecurityContextHelper() {}

    /** 현재 인증된 사용자의 UUID v7 PK — 미인증 시 null. */
    public static String currentUserId() {
        CustomUserDetails u = currentPrincipal();
        return u == null ? null : u.getUserId();
    }

    /**
     * 현재 인증된 사용자의 부서 ID — 회원/미인증/부서 미지정 시 null.
     *
     * <p>Gemini 문서검색 가시성 필터 + 업로드 폼 기본값에 사용.
     * CustomUserDetails 생성자가 v_user_login 의 빈 문자열을 null 로 정규화하므로 null 반환만 검사.
     */
    public static String currentDepartmentId() {
        CustomUserDetails u = currentPrincipal();
        return u == null ? null : u.getDepartmentId();
    }

    /** 현재 인증된 사용자의 부서명 (denormalized cache) — null/blank 시 null. */
    public static String currentDepartmentName() {
        CustomUserDetails u = currentPrincipal();
        return u == null ? null : u.getDepartmentName();
    }

    /** 현재 사용자 타입 — MEMBER/EMPLOYEE/STAFF/null. */
    public static String currentUserType() {
        CustomUserDetails u = currentPrincipal();
        return u == null ? null : u.getUserType();
    }

    /** authority 보유 여부 (ROLE_ 접두사 자동 부여). */
    public static boolean hasRole(String roleCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        String want = roleCode.startsWith("ROLE_") ? roleCode : "ROLE_" + roleCode;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (want.equals(a.getAuthority())) return true;
        }
        return false;
    }

    /** ROLE_ADMIN 보유 — 최상위 권한. Gemini 문서검색 부서 필터 우회 기준. */
    public static boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }

    /** 현재 Principal 캐스팅 — anonymous/미인증/타입 불일치 시 null. */
    public static CustomUserDetails currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        return (p instanceof CustomUserDetails cud) ? cud : null;
    }
}
