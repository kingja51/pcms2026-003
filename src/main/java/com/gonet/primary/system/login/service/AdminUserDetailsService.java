package com.gonet.primary.system.login.service;

import com.gonet.common.util.CsvUtils;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.login.dto.UserLogin;
import com.gonet.primary.system.login.mapper.UserLoginMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관리자 폼(/admin/login) 전용 UserDetailsService — user_type IN ('STAFF','EMPLOYEE') 만 조회.
 *
 * <p>tb_admin → user_type='STAFF', tb_employee → user_type='EMPLOYEE'.
 * 회원 계정이 관리자 폼으로 로그인 시도 시 UsernameNotFoundException → INVALID_CREDENTIALS.
 * <p>최종 ROLE_STAFF 이상 권한 검증 + IP 화이트리스트는 {@link AdminLoginGuard} +
 * {@code AdminLoginSuccessHandler.onPostAuthChecks} 에서 수행.
 */
@Service("adminUserDetailsService")
public class AdminUserDetailsService implements UserDetailsService {

    private static final List<String> ALLOWED = List.of("STAFF", "EMPLOYEE");

    private final UserLoginMapper mapper;

    public AdminUserDetailsService(UserLoginMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        UserLogin u = mapper.findByLoginIdInTypes(loginId, ALLOWED);
        if (u == null) throw new UsernameNotFoundException("Invalid credentials");
        // UUID 기반 role_ids (DynamicAuthorizationManager URL 권한 검사용) +
        // 텍스트 role_codes (SecurityConfig hasRole("ADMIN") 등 코드 기반 검사용) 통합
        List<String> allRoles = new ArrayList<>(CsvUtils.toList(u.getRoleIds()));
        allRoles.addAll(CsvUtils.toList(u.getRoleCodes()));
        return new CustomUserDetails(u, allRoles, Collections.emptyList());
    }
}
