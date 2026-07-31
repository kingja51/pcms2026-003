package com.gonet.primary.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원 탈퇴 폼 — 탈퇴 사유 필수(10자 이상).
 *
 * <p>전자상거래법상 탈퇴 사유는 수집·보관 대상이며, 서비스 개선 피드백으로도 활용.
 * 단순 버튼 클릭으로 즉시 탈퇴를 막고 재고 단계를 강제한다.
 */
@Getter
@Setter
public class MemberWithdrawForm {

    @NotBlank(message = "탈퇴 사유를 입력해 주세요.")
    @Size(min = 10, max = 500, message = "탈퇴 사유는 10자 이상 500자 이내로 입력해 주세요.")
    private String withdrawReason;
}
