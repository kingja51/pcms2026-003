package com.gonet.primary.admin.mapper;

import com.gonet.primary.admin.dto.AdminAllowIp;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_admin_allow_ip 조회/카운트 갱신 — 로그인 핫패스 전용.
 *
 * <p>본 매퍼는 admin CRUD 화면용이 아니라 인증 가드(AdminLoginIpGuard) 의 의존.
 * UI CRUD 는 추후 별도 MngMapper 로 분리.
 */
@EgovMapper
public interface AdminAllowIpMapper {

    /** 관리자 활성 화이트리스트 (delete_yn='N' AND use_yn='Y' AND expires_at 미만료). */
    List<AdminAllowIp> findActiveByAdminId(@Param("adminId") String adminId);

    /**
     * 전 관리자의 활성 화이트리스트 행 목록 — {@code /admin/login} pre-auth IP 게이트 전용.
     * 로그인 폼 접근 자체를 허용된 IP 에서만 가능하게 하기 위해 id 없이 조회.
     */
    List<AdminAllowIp> findAllActive();

    /** 매칭된 IP 의 access_count 증가 + last_access_at 갱신. */
    void touchAccess(@Param("ipId") String ipId);
}
