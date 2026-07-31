package com.gonet.common.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP 송신 전담 — 단순 wrapper.
 *
 * <p>{@link MailService} 의 private {@code doSend()} 에서 실제 네트워크 I/O 부분을
 * 분리해 다른 빈에서 호출 가능한 public 메서드로 노출. self-invocation 패턴 회피
 * (회복탄력성 어노테이션 도입 시 AOP 프록시가 동작하도록 했던 것의 흔적이지만,
 * 현재는 단순 송신만 담당).
 *
 * <p>회복탄력성 정책 변경 (2026-04-27):
 * <ul>
 *   <li>이전: Resilience4j {@code @Retry(3) + @CircuitBreaker} 적용</li>
 *   <li>현재: 단순 직접 송신 — 일방문 1만명 이하 소규모 CMS 에서 서킷브레이커는 오버스펙.
 *       SMTP 일시 실패는 호출 측에서 try/catch + 사용자 안내로 처리.</li>
 * </ul>
 *
 * <p>SMTP 타임아웃은 {@code spring.mail.properties.mail.smtp.connectiontimeout/timeout}
 * 으로 Jakarta Mail 단에서 5초 설정 — request thread 무한 대기 차단.
 */
@Component
public class MailTransport {

    private static final Logger log = LoggerFactory.getLogger(MailTransport.class);

    private final JavaMailSender mailSender;

    public MailTransport(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * SMTP 송신.
     *
     * @throws MailException 송신 실패 시 원 예외 전파 (호출 측이 catch 하여 사용자 안내)
     */
    public void send(MimeMessage message) {
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("MAIL_SEND transport error: {}", ex.getMessage());
            throw ex;
        }
    }
}
