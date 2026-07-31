package com.gonet.primary.notification.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.mail.MailTransport;
import com.gonet.primary.notification.dto.NotificationType;
import com.gonet.primary.notification.dto.UserContact;
import com.gonet.primary.notification.mapper.UserContactMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 알림의 email 채널 dispatch — pref 통과 후 호출되는 발송 헬퍼.
 *
 * <p>핵심 책임:
 * <ol>
 *   <li>{@link UserContactMapper} 로 user_id → email 해석 (3-way UNION, EncryptInterceptor 자동 복호화)</li>
 *   <li>제목/본문 조합. linkUrl 있으면 절대 URL 로 변환해 본문 끝에 추가</li>
 *   <li>{@link MailTransport} 로 발송 (retry/CB 정책 보유)</li>
 * </ol>
 *
 * <p>실패는 RuntimeException 으로 전파하지 않고 log WARN — 호출자(NotificationServiceImpl)
 * 의 본 트랜잭션에 영향 없음. 발송은 {@code @Async} 로 비동기 — 다건 알림에서 응답 지연 회피.
 */
@Component
public class NotificationEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailDispatcher.class);

    private final UserContactMapper userContactMapper;
    private final JavaMailSender    mailSender;
    private final MailTransport     mailTransport;
    private final AuditLogger       auditLogger;
    private final String            fromAddress;
    private final String            fromName;
    private final String            baseUrl;

    public NotificationEmailDispatcher(UserContactMapper userContactMapper,
                                         JavaMailSender mailSender,
                                         MailTransport mailTransport,
                                         AuditLogger auditLogger,
                                         @Value("${gopcms.mail.from:no-reply@gopcms.local}") String fromAddress,
                                         @Value("${gopcms.mail.from-name:GoPCMS}") String fromName,
                                         @Value("${gopcms.app.base-url:http://localhost}") String baseUrl) {
        this.userContactMapper = userContactMapper;
        this.mailSender        = mailSender;
        this.mailTransport     = mailTransport;
        this.auditLogger       = auditLogger;
        this.fromAddress       = fromAddress;
        this.fromName          = fromName;
        this.baseUrl           = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 비동기 email 발송. 호출자 트랜잭션과 분리되어 본 메서드의 실패는 호출자에 영향 없음.
     *
     * <p>{@code @Async} 가 작동하려면 별도 빈에서 호출되어야 한다 (Spring AOP self-invocation
     * 제약). NotificationServiceImpl 가 이 dispatcher 를 주입받아 호출하므로 OK.
     */
    @Async("notificationEmailExecutor")
    public void sendAsync(String recipientUserId,
                            NotificationType type,
                            String title,
                            String body,
                            String linkUrl,
                            String notificationId) {
        try {
            if (recipientUserId == null || recipientUserId.isBlank() || title == null) return;

            UserContact contact = userContactMapper.findByUserId(recipientUserId);
            if (contact == null) {
                log.info("EMAIL_SKIP — user not found userId={}", recipientUserId);
                return;
            }
            String email = contact.getEmail();
            if (email == null || email.isBlank() || !email.contains("@")) {
                log.info("EMAIL_SKIP — no email userId={}", recipientUserId);
                return;
            }

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(email);
            helper.setSubject(buildSubject(type, title));
            helper.setText(buildBody(title, body, linkUrl), false);

            mailTransport.send(mime);

            auditLogger.write(AuditEvent.of("NOTIFICATION_EMAIL_SEND", "tb_notification")
                .withTarget(notificationId == null ? recipientUserId : notificationId)
                .withResult("SUCCESS")
                .withAfter("{\"type\":\"" + type + "\",\"recipient\":\"" + maskEmail(email) + "\"}"));
            log.info("===NOTIFICATION_EMAIL_OK type={} recipient={}", type, maskEmail(email));
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.warn("NOTIFICATION_EMAIL_FAIL_BUILD user={} type={} reason={}",
                recipientUserId, type, ex.getMessage());
        } catch (Exception ex) {
            log.warn("NOTIFICATION_EMAIL_FAIL user={} type={} reason={}",
                recipientUserId, type, ex.getMessage());
        }
    }

    /** 제목 — [GoPCMS] 타입라벨 · title */
    private String buildSubject(NotificationType type, String title) {
        String label = type == null ? "알림" : type.label();
        String prefix = "[" + (fromName == null ? "GoPCMS" : fromName) + "] " + label + " · ";
        // RFC 2822 권장: subject 합리적 길이로 절단
        String full = prefix + title;
        return full.length() > 200 ? full.substring(0, 200) : full;
    }

    /** 본문 — title + body + 절대 링크. 단순 텍스트. */
    private String buildBody(String title, String body, String linkUrl) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(title).append("\n\n");
        if (body != null && !body.isBlank()) {
            sb.append(body).append("\n\n");
        }
        if (linkUrl != null && !linkUrl.isBlank()) {
            String abs = linkUrl.startsWith("http") ? linkUrl : baseUrl + linkUrl;
            sb.append("자세히 보기: ").append(abs).append("\n\n");
        }
        sb.append("-- \n").append(fromName == null ? "GoPCMS" : fromName)
          .append(" 알림 (자동 발송)\n")
          .append("이 알림을 더 이상 받지 않으려면: ").append(baseUrl).append("/notification/pref\n");
        return sb.toString();
    }

    /** 이메일 마스킹 — 감사 로그용. abc@example.com → a***@example.com */
    private static String maskEmail(String email) {
        if (email == null) return "(none)";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
