package com.gonet.primary.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원 개인정보 수정 폼 — 마이페이지에서 본인만.
 *
 * <p>loginId / 가입유형 / 본인인증 필드는 수정 불가.
 * <p>비밀번호는 별도 {@link MemberPasswordForm} 으로 분리.
 */
@Getter
@Setter
public class MemberProfileForm {

    @NotBlank @Size(max = 100)
    private String memberName;

    @Size(max = 100)
    private String nickname;

    @NotBlank @Email @Size(max = 255)
    private String email;

    @Pattern(regexp = "^(\\d{2,3}-?\\d{3,4}-?\\d{4})?$",
             message = "전화번호 형식이 올바르지 않습니다.")
    @Size(max = 20)
    private String phone;

    @Size(max = 10)
    private String addressZipcode;

    @Size(max = 1024)
    private String address;

    @Size(max = 1024)
    private String addressDetail;

    // 수신동의만 수정 가능 (필수동의는 탈퇴·재가입으로만 철회)
    private String marketingAgreeYn = "N";
    private String smsAgreeYn       = "N";
    private String emailAgreeYn     = "N";
}
