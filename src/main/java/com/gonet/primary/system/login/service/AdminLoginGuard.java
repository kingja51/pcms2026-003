package com.gonet.primary.system.login.service;

import com.gonet.common.util.CsvUtils;
import com.gonet.common.util.IpMatcher;
import com.gonet.primary.admin.dto.AdminAllowIp;
import com.gonet.primary.admin.mapper.AdminAllowIpMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 관리자 로그인 검증 가드.
 *
 * <p>책임 2가지 (Spring Security AuthenticationProvider 단위에 들어가지 않고,
 * SuccessHandler 직전 또는 별도 Filter 에서 호출):
 * <ol>
 *   <li>{@link #hasStaffOrAbove(String)} : role_ids CSV 에 ROLE_STAFF role_id 가
 *       포함되어 있는지 (closure 확장 결과 — STAFF 자신 또는 STAFF 의 조상 보유 시 true)</li>
 *   <li>{@link #ipAllowed(String, String)} : tb_admin_allow_ip 활성 행 우선,
 *       활성 행이 없으면 폴백(application.yml: gopcms.security.admin-ip.fallback-allow=true)</li>
 * </ol>
 */
@Service
public class AdminLoginGuard {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginGuard.class);

    /**
     * ROLE_STAFF role_id — DDL seed 와 동일 (00000000-0000-7000-8000-000000000014).
     * 환경별 다른 시스템에서 활용할 수 있도록 application.yml 오버라이드 가능.
     */
    private final String roleStaffId;
    private final boolean fallbackAllowWhenEmpty;

    private final AdminAllowIpMapper allowIpMapper;

    public AdminLoginGuard(
            AdminAllowIpMapper allowIpMapper,
            @Value("${gopcms.security.admin.role-staff-id:00000000-0000-7000-8000-000000000014}")
            String roleStaffId,
            @Value("${gopcms.security.admin.ip-fallback-allow:true}")
            boolean fallbackAllowWhenEmpty) {
        this.allowIpMapper = allowIpMapper;
        this.roleStaffId = roleStaffId;
        this.fallbackAllowWhenEmpty = fallbackAllowWhenEmpty;
    }

    /** role_ids CSV(closure 확장) 안에 ROLE_STAFF role_id 가 있으면 true. */
    public boolean hasStaffOrAbove(String roleIdsCsv) {
        List<String> ids = CsvUtils.toList(roleIdsCsv);
        return ids.contains(roleStaffId);
    }

    /**
     * IP 화이트리스트 검사. 매칭된 행의 access_count + last_access_at 도 함께 갱신.
     *
     * @return true = 통과, false = 차단. 활성 행이 0건이면 {@code fallbackAllowWhenEmpty}.
     */
    public boolean ipAllowed(String adminId, String clientIp) {
        if (adminId == null || adminId.isBlank() || clientIp == null || clientIp.isBlank()) {
            return false;
        }
        List<AdminAllowIp> rules = allowIpMapper.findActiveByAdminId(adminId);
        if (rules.isEmpty()) {
            log.info("ADMIN_IP_GUARD no rules adminId={} → fallback={}",
                adminId, fallbackAllowWhenEmpty);
            return fallbackAllowWhenEmpty;
        }
        for (AdminAllowIp r : rules) {
            if (matches(r, clientIp)) {
                allowIpMapper.touchAccess(r.getIpId());
                log.info("ADMIN_IP_GUARD match ipId={} adminId={} ip={}",
                    r.getIpId(), adminId, clientIp);
                return true;
            }
        }
        log.info("===ADMIN_IP_GUARD reject adminId={} ip={} rules={}",
            adminId, clientIp, rules.size());
        return false;
    }

    /**
     * pre-auth IP 게이트 — {@code /admin/login} 폼 접근 시 호출.
     * 관리자 중 어떤 한 명이라도 해당 IP 를 허용하고 있으면 true.
     *
     * <p>활성 화이트리스트가 0건이면 {@code fallbackAllowWhenEmpty} (default true)
     * 로 폴백 — 초기 세팅 시 잠금을 방지. 운영 전환 시 false 로 변경.
     */
    public boolean ipAllowedAnyAdmin(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return false;
        List<AdminAllowIp> rules = allowIpMapper.findAllActive();
        if (rules.isEmpty()) {
            log.info("ADMIN_LOGIN_IP_GATE no rules → fallback={}", fallbackAllowWhenEmpty);
            return fallbackAllowWhenEmpty;
        }
        for (AdminAllowIp r : rules) {
            if (matches(r, clientIp)) {
                log.info("ADMIN_LOGIN_IP_GATE match ipId={} ip={}", r.getIpId(), clientIp);
                return true;
            }
        }
        log.info("===ADMIN_LOGIN_IP_GATE reject ip={} rules={}", clientIp, rules.size());
        return false;
    }

    private boolean matches(AdminAllowIp rule, String clientIp) {
        return switch (rule.getIpType() == null ? "SINGLE" : rule.getIpType().toUpperCase()) {
            case AdminAllowIp.TYPE_RANGE  -> IpMatcher.matchesRange(rule.getIpStart(), rule.getIpEnd(), clientIp);
            case AdminAllowIp.TYPE_CIDR   -> IpMatcher.matchesCidr(rule.getIpAddress(), clientIp);
            default                        -> IpMatcher.matchesSingle(rule.getIpAddress(), clientIp);
        };
    }
}
