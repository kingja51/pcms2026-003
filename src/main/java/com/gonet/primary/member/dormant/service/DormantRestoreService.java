package com.gonet.primary.member.dormant.service;

import com.gonet.primary.member.dto.Member;

/**
 * 휴면 해제 본인확인 — <b>실명인증 / 이메일 OTP 택1</b> (개발가이드 §10-6).
 *
 * <p>두 경로 모두 성공하면 같은 곳으로 간다: {@link DormantService#restoreVerified}
 * 역이관 + {@code restored_at} 기록. 확인 수단만 다르고 이후는 동일하다.
 *
 * <h2>계정 열거(enumeration)를 막는 것이 이 인터페이스의 계약이다</h2>
 * 휴면 해제 화면은 <b>로그인 전에 누구나 접근한다</b>. 응답이 갈리면 그 자체가
 * "이 아이디는 휴면 상태로 존재한다" 는 정보가 된다. 그래서:
 * <ul>
 *   <li>{@link #requestOtp} 는 <b>어떤 경우에도 예외를 던지지 않는다.</b>
 *       계정이 없어도, 이메일이 달라도, 쿨다운에 걸려도 정상 종료한다.
 *       호출자는 언제나 같은 화면을 보여야 한다</li>
 *   <li>소요 시간도 맞춘다 — 존재하는 계정만 메일을 보내느라 느리면
 *       응답 시간이 곧 존재 신호다</li>
 *   <li>{@link #restoreByOtp} 실패는 사유를 구분하지 않는다.
 *       만료·오답·미존재가 모두 같은 예외 메시지다</li>
 * </ul>
 */
public interface DormantRestoreService {

    /**
     * 수단 B 1단계 — 인증번호 발송 요청.
     *
     * <p>휴면 스냅샷의 이메일로만 보낸다. <b>입력 이메일의 해시가 스냅샷과 일치할 때만</b>
     * 발송한다 — 아이디만 알면 남의 계정 메일함으로 코드를 쏠 수 있으면 안 된다.
     *
     * <p><b>절대 예외를 던지지 않는다.</b> 실패는 전부 안에서 삼키고 로그로만 남긴다.
     */
    void requestOtp(String loginId, String email, String clientIp);

    /**
     * 수단 B 2단계 — 인증번호 검증 후 해제.
     *
     * @throws IllegalArgumentException 확인 실패. <b>사유를 구분하지 않는 단일 메시지</b>
     * @return 복원된 회원
     */
    Member restoreByOtp(String loginId, String email, String code);

    /**
     * 수단 A — 실명인증(NICE) 결과의 DI 해시를 휴면 스냅샷과 대조 후 해제.
     *
     * <p>DI 는 개인을 식별하는 값이라 아이디를 몰라도 본인 계정을 찾을 수 있다.
     * 아이디 입력을 함께 받아 좁히되, 대조 기준은 {@code di_hash} 다.
     *
     * @param diHash NICE 복호화 결과의 DI 를 HMAC 한 값
     * @throws IllegalArgumentException DI 불일치 또는 대상 없음
     */
    Member restoreByIdentity(String loginId, String diHash);
}
