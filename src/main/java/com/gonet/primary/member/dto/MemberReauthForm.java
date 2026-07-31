package com.gonet.primary.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 마이페이지 민감 페이지(개인정보 수정 · 탈퇴) 진입 전 재인증 폼.
 *
 * <p>3요소 검증 — 이름(평문) + 이메일(HMAC-SHA256 해시 비교) + 비밀번호(BCrypt) 모두 일치 시 통과.
 * <p>CI 같은 본인확인 서비스 결과는 현재 미연동 — 이메일 경로가 대체 신원 증명 축.
 */
@Getter
@Setter
public class MemberReauthForm {

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 100)
    private String memberName;

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(max = 100)
    private String password;

    /**
     * 재인증 후 이동할 대상 페이지 — profile(개인정보 수정) / withdraw(탈퇴).
     * 공격자 제어 URL 로의 open-redirect 방지 위해 화이트리스트 패턴 강제.
     */
    @Pattern(regexp = "^(profile|withdraw)$",
             message = "허용되지 않은 next 값입니다.")
    private String next = "profile";
}
