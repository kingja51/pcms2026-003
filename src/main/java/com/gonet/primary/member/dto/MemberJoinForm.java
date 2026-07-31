package com.gonet.primary.member.dto;

import com.gonet.common.validator.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 회원 가입 폼 — 비로그인 상태 접근 (/member/join).
 *
 * <p>필수 동의: 이용약관(terms) + 개인정보(privacy). 마케팅·SMS·이메일은 선택.
 * <p>비밀번호 확인(passwordConfirm) 일치는 {@link #isPasswordMatched()} AssertTrue 로 검증.
 *
 * <p>회원 유형:
 * <ul>
 *   <li>{@code ADULT} — 일반(만 14세 이상). 본인이 직접 본인인증. 세션 DI → di/di_hash</li>
 *   <li>{@code CHILD} — 14세 미만. 부모가 본인인증. 세션 DI → (di = parent_di),
 *       부모 이름·DI 는 {@code parent_name/parent_di/parent_di_hash} 에도 저장</li>
 * </ul>
 */
@Getter
@Setter
public class MemberJoinForm {

    /** 가입 유형. ADULT / CHILD. Step 1 에서 선택. */
    @NotBlank
    @Pattern(regexp = "^(ADULT|CHILD)$", message = "가입 유형이 올바르지 않습니다.")
    private String userType = "ADULT";

    /** 14세 미만 가입 시 법정대리인(부모) 이름. CHILD 모드에서 필수. */
    @Size(max = 100)
    private String parentName;

    @NotBlank
    @Size(min = 8, max = 50, message = "아이디는 8자 이상 50자 이내여야 합니다.")
    @Pattern(regexp = "^[a-z][a-z0-9_]+$",
             message = "로그인 ID 는 영소문자/숫자/언더스코어만 (첫 글자 영소문자)")
    private String loginId;

    @NotBlank @Size(max = 100)
    @PasswordPolicy
    private String password;

    @NotBlank @Size(max = 100)
    private String passwordConfirm;

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

    @NotBlank(message = "생년월일을 입력해 주세요.")
    @Pattern(regexp = "^\\d{8}$",
             message = "생년월일은 YYYYMMDD 8자리입니다.")
    private String birthDate;

    @NotBlank(message = "성별을 선택해 주세요.")
    @Pattern(regexp = "^[MF]$",
             message = "성별은 남성(M) 또는 여성(F) 만 선택할 수 있습니다.")
    private String gender;

    // 약관 동의
    @NotBlank @Pattern(regexp = "Y", message = "이용약관에 동의해야 가입할 수 있습니다.")
    private String termsAgreeYn;

    @NotBlank @Pattern(regexp = "Y", message = "개인정보 수집·이용에 동의해야 가입할 수 있습니다.")
    private String privacyAgreeYn;

    private String marketingAgreeYn = "N";
    private String smsAgreeYn       = "N";
    private String emailAgreeYn     = "N";

    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    public boolean isPasswordMatched() {
        if (password == null || passwordConfirm == null) return true; // NotBlank 가 먼저 잡음
        return password.equals(passwordConfirm);
    }

    /**
     * 생년월일이 유효한 일자인지 + 미래 날짜가 아닌지 검증.
     * 형식(@Pattern) / 필수(@NotBlank) 는 별도 어노테이션이 먼저 잡음.
     */
    @AssertTrue(message = "유효한 생년월일이 아닙니다.")
    public boolean isBirthDateValid() {
        if (birthDate == null || birthDate.isBlank()) return true;          // NotBlank 가 먼저 잡음
        if (!birthDate.matches("^\\d{8}$"))           return true;          // Pattern 이 먼저 잡음
        try {
            LocalDate b = LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            return !b.isAfter(LocalDate.now());                              // 미래 일자 거부 (2026-02-31 등 invalid 도 여기서 throw)
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    /** CHILD 모드에서는 부모 이름(parentName) 필수. */
    @AssertTrue(message = "14세 미만 가입은 법정대리인(부모) 이름이 필요합니다.")
    public boolean isParentNameRequiredWhenChild() {
        if (!"CHILD".equals(userType)) return true;
        return parentName != null && !parentName.isBlank();
    }

    /**
     * CHILD 모드에서는 자녀 생년월일 필수 + 만 14세 미만이어야 한다.
     * 만 14세 이상이면 일반(ADULT) 가입 흐름으로 안내.
     */
    @AssertTrue(message = "14세 미만 가입은 만 14세 미만의 생년월일을 입력해야 합니다.")
    public boolean isBirthDateValidWhenChild() {
        if (!"CHILD".equals(userType)) return true;
        if (birthDate == null || birthDate.isBlank()) return false;
        LocalDate birth;
        try {
            birth = LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException ex) {
            return false; // @Pattern 이 형식은 잡지만 2026-02-30 같은 무효 일자는 여기서 차단
        }
        LocalDate today = LocalDate.now();
        if (birth.isAfter(today)) return false;
        return Period.between(birth, today).getYears() < 14;
    }
}
