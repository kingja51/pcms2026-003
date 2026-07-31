package com.gonet.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link PasswordPolicy} 제약 구현.
 *
 * <p>길이와 문자 종류 수만 본다. null/빈 문자열은 false (필요하면 @NotBlank 와 함께 사용).
 */
public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {

    @Override
    public boolean isValid(String pw, ConstraintValidatorContext ctx) {
        // 관례상 null/empty 는 통과 — 존재 강제는 @NotBlank 가 담당. 이로써
        // "수정 시 비밀번호 미입력이면 변경 없음" 같은 선택 필드 시나리오가 성립한다.
        if (pw == null || pw.isEmpty()) return true;
        int len = pw.length();
        if (len < 8) return false;

        boolean upper = false, lower = false, digit = false, special = false;
        for (int i = 0; i < len; i++) {
            char c = pw.charAt(i);
            if      (c >= 'A' && c <= 'Z') upper = true;
            else if (c >= 'a' && c <= 'z') lower = true;
            else if (c >= '0' && c <= '9') digit = true;
            else                            special = true;
        }
        int types = (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);

        if (len < 10) return types >= 3;
        return types >= 2;
    }
}
