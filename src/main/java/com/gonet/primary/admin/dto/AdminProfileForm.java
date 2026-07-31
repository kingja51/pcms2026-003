package com.gonet.primary.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 본인 개인정보 수정 폼 — {@code /admin/mypage/profile}.
 *
 * <p>수정 가능 필드: 이름/이메일/전화/부서.
 * <p>수정 불가: loginId, adminGroupId, role_ids, 2FA 설정(별도), 잠금상태,
 *   접근 제어(ipWhitelist / allowedTime — 시스템 관리자만 가능).
 */
@Getter
@Setter
public class AdminProfileForm {

    @NotBlank @Size(max = 100)
    private String adminName;

    @NotBlank @Email @Size(max = 255)
    private String email;

    @Pattern(regexp = "^(\\d{2,3}-?\\d{3,4}-?\\d{4})?$",
             message = "전화번호 형식이 올바르지 않습니다.")
    @Size(max = 20)
    private String phone;

    /** 부서 ID — FK tb_department. nullable. */
    @Size(max = 40)
    private String departmentId;
}
