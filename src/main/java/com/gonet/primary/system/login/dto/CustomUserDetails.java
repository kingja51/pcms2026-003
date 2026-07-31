package com.gonet.primary.system.login.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Spring Security Principal — v_user_login + 역할 계층 확장 결과.
 *
 * <p>권한(Authority) 구성:
 * <ul>
 *   <li>{@code ROLE_{userType}}     — MEMBER/STAFF (사용자 타입 자체).
 *       003 은 직원(EMPLOYEE)을 로그인 주체에서 제외했다 — v_user_login 은 MEMBER·STAFF 2종이다</li>
 *   <li>role_ids CSV 의 각 role_code 에 대해 {@code ROLE_{code}}</li>
 *   <li>tb_role_auth 로부터 조회한 세밀권한 (authority name, e.g. CONTENT_PUBLISH)</li>
 * </ul>
 *
 * <p>프로퍼티:
 * <ul>
 *   <li>{@code userId}  : UUID v7 PK</li>
 *   <li>{@code uniqId}  : BIGINT seq (DB 업무키, 로그·감사에서 주로 사용)</li>
 *   <li>{@code roleIds} : CSV (계층 확장 포함)</li>
 * </ul>
 */
@Getter
@EqualsAndHashCode(of = {"userType", "userId"})
public class CustomUserDetails implements UserDetails {

    private final String userType;
    private final String userId;
    private final Long   uniqId;
    private final String siteId;
    private final String groupId;
    /** 부서 ID — 직원/관리자만, 회원/미지정은 null. Gemini 문서검색 가시성 필터 + 업로드 기본값. */
    private final String departmentId;
    /** 부서명 — denormalized cache. v_user_login 의 department_name 컬럼에서 로드. */
    private final String departmentName;
    private final String loginId;
    private final String password;
    private final String status;
    private final LocalDateTime lockedUntil;
    /** 직전 로그인 일시 — 로그인 SuccessHandler 가 tb_*.last_login_at 을 덮어쓰기 전의 값. */
    private final LocalDateTime lastLoginAt;
    private final String roleIds;
    /** v_user_login.role_codes CSV — 예: "ROLE_ADMIN,ROLE_STAFF,ROLL_MANAGER".
     *  DynamicAuthorizationManager 의 allowed_user_types 매칭이 user_type 외에도 role_code 를 인식. */
    private final String roleCodes;
    private final String groupIds;
    private final boolean twoFactorEnabled;
    private final Collection<GrantedAuthority> authorities;

    public CustomUserDetails(UserLogin u, List<String> roleCodes, List<String> fineAuths) {
        this.userType   = u.getUserType();
        this.userId     = u.getUserId();
        this.uniqId     = u.getUniqId();
        this.siteId     = u.getSiteId();
        this.groupId    = u.getGroupId();
        this.departmentId = blankToNull(u.getDepartmentId());
        this.departmentName = blankToNull(u.getDepartmentName());
        this.loginId    = u.getLoginId();
        this.password   = u.getPassword();
        this.status     = u.getStatus();
        this.lockedUntil = u.getLockedUntil();
        this.lastLoginAt = u.getLastLoginAt();
        this.roleIds    = u.getRoleIds();
        this.roleCodes  = u.getRoleCodes();
        this.groupIds   = u.getGroupIds();
        this.twoFactorEnabled = "Y".equalsIgnoreCase(u.getTwoFactorEnabledYn());
        this.authorities = Stream.concat(
                Stream.concat(
                    Stream.of(new SimpleGrantedAuthority("ROLE_" + u.getUserType())),
                    roleCodes.stream().map(c -> new SimpleGrantedAuthority(
                        c.startsWith("ROLE_") ? c : "ROLE_" + c))
                ),
                fineAuths.stream().map(SimpleGrantedAuthority::new)
            ).map(a -> (GrantedAuthority) a)
             .toList();
    }

    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()              { return password; }
    @Override public String getUsername()              { return loginId; }
    @Override public boolean isAccountNonExpired()     { return true; }

    @Override
    public boolean isAccountNonLocked() {
        if ("LOCKED".equalsIgnoreCase(status)) return false;
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) return false;
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // 만료 정책은 S1 에서 tb_*_password_history 기반으로 강화
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public static Collection<GrantedAuthority> emptyAuthorities() {
        return Collections.emptyList();
    }

    /** v_user_login 의 빈 문자열('')을 null 로 정규화 — MEMBER department_id/name 처리. */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
