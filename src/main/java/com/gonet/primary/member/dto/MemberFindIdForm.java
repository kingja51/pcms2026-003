package com.gonet.primary.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원 ID 찾기 폼 — 가입 시 등록한 이름 + 이메일 두 항목 동시 일치 시에만 ID 노출.
 *
 * <p>2026-05-25 강화 — 이메일 단독 매칭은 봇/스캐너에 의해 회원 ID enumeration
 * 가능성이 있어 이름 동시 일치 정책으로 전환. 이름은 tb_member.member_name 평문
 * 컬럼과 SQL 단 AND 매칭 (이름은 @Encrypt 가 아닌 평문 컬럼이라 직접 비교 가능).
 */
@Getter
@Setter
public class MemberFindIdForm {

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(min = 1, max = 100, message = "이름은 100자 이내입니다.")
    private String name;

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    private String email;
}
