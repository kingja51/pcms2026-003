package com.gonet.primary.admin.dto;

import com.gonet.common.validator.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 본인 비밀번호 변경 폼 — 현재 비밀번호 검증 필요.
 */
@Getter
@Setter
public class AdminPasswordForm {

    @NotBlank
    private String currentPassword;

    @NotBlank @Size(max = 100)
    @PasswordPolicy
    private String newPassword;

    @NotBlank @Size(max = 100)
    private String newPasswordConfirm;

    @AssertTrue(message = "새 비밀번호가 일치하지 않습니다.")
    public boolean isNewPasswordMatched() {
        if (newPassword == null || newPasswordConfirm == null) return true;
        return newPassword.equals(newPasswordConfirm);
    }
}
