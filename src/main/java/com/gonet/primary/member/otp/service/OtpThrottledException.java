package com.gonet.primary.member.otp.service;

/**
 * 발급 제한(쿨다운·시간당 상한)에 걸렸다.
 *
 * <p>주의: 이 예외를 <b>사용자 화면에 그대로 노출하면 계정 열거 통로</b>가 된다.
 * "잠시 후 다시 시도" 는 곧 "그 계정은 존재한다" 는 뜻이기 때문이다.
 * 휴면 해제처럼 열거를 막아야 하는 경로에서는 호출자가 이 예외를 삼키고
 * 성공과 동일한 화면을 보여야 한다({@code DormantRestoreServiceImpl} 참조).
 */
public class OtpThrottledException extends RuntimeException {

    public OtpThrottledException(String message) {
        super(message);
    }
}
