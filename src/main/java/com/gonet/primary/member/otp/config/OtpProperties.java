package com.gonet.primary.member.otp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OTP 정책값 — {@code gopcms.member.otp.*}.
 *
 * <p>자릿수·유효시간·시도제한·쿨다운을 코드에 박지 않는다(PLAN P5). 사고가 나면
 * 배포 없이 조일 수 있어야 하고, 값의 근거를 {@code application.yml} 주석에 남겨야
 * "왜 5분인가" 를 나중에 되짚을 수 있다.
 *
 * <p>기본값은 안전한 쪽으로 둔다 — yml 이 비어도 무제한이 되지 않는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.member.otp")
public class OtpProperties {

    /** 코드 자릿수. 6자리 = 100만 조합, 시도 5회 제한과 함께 쓰면 대입 성공률 5e-6. */
    private int length = 6;

    /** 유효시간. 짧을수록 안전하지만 메일 지연을 견뎌야 한다. */
    private Duration ttl = Duration.ofMinutes(5);

    /** 검증 시도 상한 — 초과하면 코드를 폐기한다(재발급을 강제). */
    private int maxAttempts = 5;

    /** 재발송 쿨다운 — 메일 폭탄(피해자 메일함 공격) 차단. */
    private Duration resendCooldown = Duration.ofSeconds(60);

    /** 계정당 시간당 발급 상한 — 쿨다운을 견디며 반복하는 저속 남용 차단. */
    private int maxPerHour = 5;
}
