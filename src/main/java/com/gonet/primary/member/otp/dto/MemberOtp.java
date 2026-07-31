package com.gonet.primary.member.otp.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원 인증번호(OTP) — {@code tb_member_otp}.
 *
 * <p><b>평문 코드는 이 DTO 에도, DB 에도 없다.</b> {@code codeHash} 만 오간다.
 * 평문은 발급 시점에 메일로 보내고 메모리에서 버린다 — DB 가 유출돼도 코드를
 * 되돌릴 수 없어야 한다(PLAN P5 DoD: "DB 에 평문 코드가 남지 않음 확인").
 *
 * <p>{@code attemptCount} 를 세션이 아니라 <b>행에 두는 이유</b>: 세션에 두면
 * 쿠키를 버리고 다시 요청하는 것만으로 시도 횟수가 초기화된다. 무제한 대입이 된다.
 */
@Getter
@Setter
public class MemberOtp {

    /** OTP ID — {@code MOT_} + UUID v7. */
    private String otpId;

    /** 대상 회원 ID. 휴면 해제는 {@code tb_member_dormant.member_id} 를 가리킨다. */
    private String memberId;

    private String siteId;

    /** 용도 — 교차 사용 방지. {@link OtpPurpose} 이름과 동일. */
    private String purpose;

    /** HMAC-SHA256(코드) 64자 hex. 평문 금지. */
    private String codeHash;

    /** 만료 일시 = 발급 + TTL. */
    private LocalDateTime expiresAt;

    /** 검증 시도 횟수 — 상한 초과 시 행을 폐기한다. */
    private int attemptCount;

    /** 검증 성공 일시. {@code null} 이면 미사용 — 1회용 소비 여부의 판정 기준. */
    private LocalDateTime verifiedAt;

    /** 발급 요청 IP — 남용 추적용. */
    private String clientIp;

    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;

    /** 만료 여부 — 기준 시각을 주입받아 테스트 가능하게 둔다. */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || !now.isBefore(expiresAt);
    }

    /** 이미 소비된 코드인가. */
    public boolean isConsumed() {
        return verifiedAt != null;
    }
}
