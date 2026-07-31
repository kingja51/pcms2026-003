package com.gonet.primary.member.otp.dto;

/**
 * OTP 용도 — {@code tb_member_otp.purpose} ({@code chk_otp_purpose} CHECK 와 1:1).
 *
 * <p>용도를 나누는 이유는 <b>교차 사용 방지</b>다. 이메일 인증용으로 받은 코드를
 * 휴면 해제에 쓸 수 있으면, 공격자는 가장 방어가 약한 발급 경로 하나만 뚫으면 된다.
 * 검증 시 purpose 까지 일치해야 통과한다.
 */
public enum OtpPurpose {

    /** 휴면 계정 해제 본인확인 (개발가이드 §10-6 수단 B). */
    DORMANT_RESTORE,

    /** 가입·변경 시 이메일 소유 확인. */
    EMAIL_VERIFY,

    /** 비밀번호 재설정. */
    PASSWORD_RESET
}
