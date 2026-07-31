package com.gonet.primary.system.login.dto;

import com.gonet.common.validator.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원/관리자 로그인 폼 형식 검증 전용 DTO.
 *
 * <p>{@code com.gonet.config.filter.LoginFormatValidationFilter} 가
 * UsernamePasswordAuthenticationFilter 앞단에서 이 DTO 로 Bean Validation 을 수행한다.
 * <p>실제 인증(Credential 비교)은 Spring Security 가 담당하므로 본 DTO 는
 * <b>형식(길이·문자 구성)</b> 만 강제한다.
 */
@Getter
@Setter
public class LoginCredential {

    @NotBlank(message = "아이디를 입력해 주세요.")
    @Size(min = 8, max = 50, message = "아이디는 8자 이상이어야 합니다.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @PasswordPolicy
    private String password;
}
