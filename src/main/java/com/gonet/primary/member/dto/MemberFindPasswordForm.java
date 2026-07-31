package com.gonet.primary.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원 비밀번호 찾기 폼 — 로그인 ID + 가입 이메일로 본인확인 후 임시 비밀번호 발송.
 */
@Getter
@Setter
public class MemberFindPasswordForm {

    @NotBlank(message = "아이디를 입력해 주세요.")
    @Size(max = 50)
    private String loginId;

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    private String email;
}
