package com.gonet.common.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 정책 제약 — GoPCMS 공통 규칙.
 *
 * <ul>
 *   <li>8자 이상 &amp; 영문 대문자·소문자·숫자·특수기호 중 <b>3종 이상</b> 조합</li>
 *   <li>또는 10자 이상 &amp; 영문 대문자·소문자·숫자·특수기호 중 <b>2종 이상</b> 조합</li>
 * </ul>
 *
 * <p>로그인 · 회원가입 · 비밀번호 변경 폼 어느 곳에서나 재사용 가능.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordPolicy {
    String message() default "비밀번호는 8자 이상 3종 조합 또는 10자 이상 2종 조합(영문 대/소문자·숫자·특수기호)이어야 합니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
