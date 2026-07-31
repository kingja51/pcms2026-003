package com.gonet.primary.admin.service;

import com.gonet.primary.admin.dto.Admin;
import com.gonet.primary.admin.dto.AdminGroupOption;
import com.gonet.primary.admin.dto.AdminPasswordForm;
import com.gonet.primary.admin.dto.AdminProfileForm;
import com.gonet.primary.admin.dto.AdminSaveForm;
import com.gonet.primary.admin.dto.AdminSearch;
import com.gonet.primary.admin.dto.RoleOption;

import java.util.List;

/**
 * 관리자(tb_admin) 서비스 — CRUD + 역할 매핑 + 비밀번호 변경 + 탈퇴.
 *
 * <p>접근 제어: {@code /admin/system/admin/**} 는 ROLE_SUPER_ADMIN 만 허용 (tb_role_url_access 규칙).
 */
public interface AdminService {

    // 조회
    List<Admin> search(AdminSearch search);
    int count(AdminSearch search);
    Admin get(String adminId);
    List<String> getDirectRoleIds(String adminId);

    // 옵션
    List<AdminGroupOption> listGroups();
    List<RoleOption>       listRoles();

    // CUD
    String create(AdminSaveForm form);
    void   update(AdminSaveForm form);
    void   resetPassword(String adminId, String newPassword);
    void   softDelete(String adminId, String reason);
    /** 관리자 계정 잠금 강제 해제 — login_fail_count=0, locked_until=NULL. */
    void   unlock(String adminId);

    /** 로그인 ID 중복확인. true=이미 사용 중, false=사용 가능. */
    boolean existsLoginId(String loginId);

    /**
     * 관리자 셀프 2FA 활성화 — secret 저장 + twoFactorEnabledYn='Y'.
     * TwoFactorUsrController 의 setup 흐름 마무리 단계. 감사 로그는 호출 측 책임.
     */
    void enableTwoFactor(String adminId, String secret);

    // 마이페이지 (본인 self-service)
    /** 관리자 본인 프로필 수정 (이름/이메일/전화/조직정보). */
    void updateSelfProfile(String adminId, AdminProfileForm form);
    /** 관리자 본인 비밀번호 변경 — 현재 비밀번호 검증 + 이력 기록. */
    void changePassword(String adminId, AdminPasswordForm form);
}
