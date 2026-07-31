package com.gonet.primary.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 본인 탈퇴 폼 — 탈퇴 사유 필수(10자 이상).
 *
 * <p>관리자 탈퇴는 시스템 영향이 크므로 사유를 감사용으로 {@code tb_admin_withdraw} 에 기록.
 */
@Getter
@Setter
public class AdminWithdrawForm {

    @NotBlank(message = "탈퇴 사유를 입력해 주세요.")
    @Size(min = 10, max = 500, message = "탈퇴 사유는 10자 이상 500자 이내로 입력해 주세요.")
    private String withdrawReason;
}
