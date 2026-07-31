package com.gonet.primary.member.otp.service;

import com.gonet.primary.member.otp.dto.OtpPurpose;

/**
 * 회원 인증번호(OTP) 발급·검증.
 *
 * <p>이 서비스는 <b>코드의 생성·저장·검증만</b> 책임진다. 누구에게 보낼지, 성공 후
 * 무엇을 할지는 호출자(예: 휴면 해제)가 정한다. 그래서 용도가 늘어도
 * ({@link OtpPurpose}) 이 서비스는 그대로다.
 *
 * <p><b>평문 코드는 {@link #issue} 의 반환값으로만 존재한다.</b> DB 에는 HMAC 만
 * 남고 이 서비스는 평문을 보관하지 않는다. 호출자가 즉시 메일로 보내고 버려야 한다.
 */
public interface MemberOtpService {

    /**
     * 코드 발급. 이전 미소비 코드는 폐기하고 새로 만든다 — 동시에 여러 코드가
     * 유효하면 공격자의 시도 기회가 배로 늘어난다.
     *
     * @return 평문 코드. <b>로그에 남기지 말 것.</b>
     * @throws OtpThrottledException 재발송 쿨다운 미경과 또는 시간당 상한 초과
     */
    String issue(String memberId, String siteId, OtpPurpose purpose, String clientIp);

    /**
     * 코드 검증 + 소비(1회용). 성공하면 그 코드는 즉시 무효가 된다.
     *
     * <p>실패 사유를 구분해 돌려주지 않는다 — 만료인지 오답인지 알려 주면
     * 공격자가 유효한 코드의 존재를 확인할 수 있다. 호출자는 성공/실패만 본다.
     *
     * @return 검증 통과 여부
     */
    boolean verifyAndConsume(String memberId, OtpPurpose purpose, String code);
}
