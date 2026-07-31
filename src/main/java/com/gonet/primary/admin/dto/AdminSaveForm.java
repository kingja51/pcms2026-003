package com.gonet.primary.admin.dto;

import com.gonet.common.validator.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 등록/수정 폼.
 *
 * <p>생성 시: {@code adminId} 는 서버 발급(UUID v7). 비밀번호 필수(BCrypt 인코딩 서비스).
 * <p>수정 시: 비밀번호는 별도 엔드포인트({@code /reset-password})에서 변경 — 본 폼에서는 선택.
 * <p>{@code roleIds}: 체크박스 다중선택 결과(tb_role.role_id 리스트) — 직계만,
 *    CSV 확장은 서비스에서 {@code sp_rebuild_admin_role_csv} 로 처리.
 */
@Getter
@Setter
public class AdminSaveForm {

    private String adminId;          // 수정 시 hidden

    @NotBlank
    @Size(max = 40)
    private String adminGroupId;

    @NotBlank
    @Size(min = 8, max = 50, message = "아이디는 8자 이상 50자 이내여야 합니다.")
    @Pattern(regexp = "^[a-z][a-z0-9_]+$",
             message = "로그인 ID 는 영소문자/숫자/언더스코어만 (첫 글자 영소문자)")
    private String loginId;

    @Size(max = 100)
    @PasswordPolicy
    private String password;         // 생성 시 필수, 수정 시 무시(별도 엔드포인트) — null/empty 통과

    @NotBlank @Size(max = 100)
    private String adminName;

    @NotBlank @Email @Size(max = 255)
    private String email;

    @Pattern(regexp = "^(\\d{2,3}-?\\d{3,4}-?\\d{4})?$",
             message = "전화번호 형식이 올바르지 않습니다.")
    @Size(max = 20)
    private String phone;

    /** 부서 ID — FK tb_department. nullable (전역 관리자 등). */
    @Size(max = 40)
    private String departmentId;

    @Pattern(regexp = "^(ACTIVE|LOCKED|INACTIVE|SUSPENDED)$")
    private String status = "ACTIVE";

    private String twoFactorEnabledYn = "N";

    @Size(max = 2000)
    private String ipWhitelist;

    @Size(max = 1000)
    private String remarks;

    /** 선택된 직계 role_id 목록 (체크박스). */
    private List<String> roleIds = new ArrayList<>();

    public void setRoleIds(List<String> v) {
        this.roleIds = (v == null ? new ArrayList<>() : v);
    }
}
