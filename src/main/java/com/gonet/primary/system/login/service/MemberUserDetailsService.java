package com.gonet.primary.system.login.service;

import com.gonet.common.util.CsvUtils;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.login.dto.UserLogin;
import com.gonet.primary.system.login.mapper.UserLoginMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 회원 폼(/member/login) 전용 UserDetailsService — user_type='MEMBER' 만 조회.
 *
 * <p>관리자 계정이 회원 폼으로 우회 로그인 하는 것을 원천 차단.
 */
@Service("memberUserDetailsService")
public class MemberUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(MemberUserDetailsService.class);
    private static final List<String> ALLOWED = List.of("MEMBER");

    private final UserLoginMapper mapper;

    public MemberUserDetailsService(UserLoginMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        UserLogin u = mapper.findByLoginIdInTypes(loginId, ALLOWED);
        if (u == null) throw new UsernameNotFoundException("Invalid credentials");
        
        String roleIds = u.getRoleIds();
        if ("MEMBER".equals(u.getUserType()) && (roleIds == null || roleIds.isBlank())) {
            roleIds = "00000000-0000-7000-8000-000000000015";
            u.setRoleIds(roleIds);
            log.info("===[MemberUserDetailsService] MEMBER 권한(Role)이 없어 기본 Role ID {} 를 세팅합니다. (loginId: {})", roleIds, loginId);
        }

        // UUID role_ids (DynamicAuthorizationManager용) + role_codes (hasRole 체크용) 통합
        List<String> allRoles = new ArrayList<>(CsvUtils.toList(roleIds));
        allRoles.addAll(CsvUtils.toList(u.getRoleCodes()));
        return new CustomUserDetails(u, allRoles, Collections.emptyList());
    }
}
