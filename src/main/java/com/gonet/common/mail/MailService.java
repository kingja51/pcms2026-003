package com.gonet.common.mail;

import com.gonet.primary.system.mail.dto.MailTemplate;
import com.gonet.primary.system.mail.service.MailTemplateService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 메일 발송 공통 서비스 — 2가지 경로 지원:
 *
 * <ol>
 *   <li>{@link #sendHtml(String, String, String, Map)} : classpath 파일 경로 템플릿
 *       (기본 {@code SpringTemplateEngine}). 레거시/관리자 미관여 메일에 사용.</li>
 *   <li>{@link #sendFromTemplate(String, String, Map)} : DB({@code tb_mail_template}) 의
 *       HTML 본문 + 제목을 StringTemplateResolver 기반 {@code mailStringTemplateEngine}
 *       으로 렌더링. 관리자가 실시간 편집 가능.</li>
 * </ol>
 *
 * <p>발신자 기본값은 {@code spring.mail.username}. DB 템플릿에 {@code sender_email} /
 * {@code sender_name} 이 있으면 오버라이드.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender        mailSender;
    private final MailTransport         transport;
    private final SpringTemplateEngine  fileEngine;
    private final StringTemplateEngine  stringEngine;
    private final MailTemplateService   templateService;
    private final String                defaultFrom;

    public MailService(JavaMailSender mailSender,
                       MailTransport transport,
                       SpringTemplateEngine fileEngine,
                       StringTemplateEngine stringEngine,
                       MailTemplateService templateService,
                       @Value("${spring.mail.username:}") String defaultFrom) {
        this.mailSender = mailSender;
        this.transport = transport;
        this.fileEngine = fileEngine;
        this.stringEngine = stringEngine;
        this.templateService = templateService;
        this.defaultFrom = defaultFrom;
    }

    // ==================================================================
    // (1) classpath 파일 기반
    // ==================================================================

    public void sendHtml(String to, String subject, String templateName, Map<String, Object> model)
            throws MessagingException {
        Context ctx = new Context();
        if (model != null) model.forEach(ctx::setVariable);
        String html = fileEngine.process(templateName, ctx);
        doSend(defaultFrom, null, to, subject, html, "file:" + templateName);
    }

    // ==================================================================
    // (2) DB 템플릿 기반 — 관리자 편집 대응
    // ==================================================================

    /**
     * {@code tb_mail_template} 의 활성 템플릿으로 메일 발송.
     *
     * @param templateCode 템플릿 코드 (예: MEMBER_WELCOME)
     * @param to           수신자 이메일 (평문)
     * @param model        Thymeleaf 변수 맵
     */
    public void sendFromTemplate(String templateCode, String to, Map<String, Object> model)
            throws MessagingException {
        MailTemplate tpl = templateService.getByCodeActive(templateCode);
        if (tpl == null || tpl.getBodyHtml() == null || tpl.getBodyHtml().isBlank()) {
            throw new IllegalStateException(
                "활성 메일 템플릿을 찾을 수 없거나 본문이 비어 있습니다: " + templateCode);
        }
        Context ctx = new Context();
        if (model != null) model.forEach(ctx::setVariable);

        // 제목도 Thymeleaf 변수 치환을 거친다 (예: [GoPCMS] {{siteName}} 가입을 환영합니다)
        String subject = stringEngine.process(tpl.getSubject(), ctx);
        String html    = stringEngine.process(tpl.getBodyHtml(), ctx);

        doSend(
            tpl.getSenderEmail() == null || tpl.getSenderEmail().isBlank()
                ? defaultFrom : tpl.getSenderEmail(),
            tpl.getSenderName(),
            to, subject, html, "db:" + templateCode);
    }

    /**
     * 미리보기 — 실제 발송 없이 렌더링 결과만 반환. 관리자 화면에서 사용.
     */
    public RenderedMail render(String templateCode, Map<String, Object> model) {
        MailTemplate tpl = templateService.getByCodeActive(templateCode);
        if (tpl == null) {
            throw new IllegalArgumentException("템플릿을 찾을 수 없습니다: " + templateCode);
        }
        return renderOf(tpl, model);
    }

    /** 원시 subject/bodyHtml 문자열에 대한 미리보기 — 편집 중인 값으로 즉시 확인. */
    public RenderedMail renderRaw(String subject, String bodyHtml, Map<String, Object> model) {
        Context ctx = new Context();
        if (model != null) model.forEach(ctx::setVariable);
        String s = subject == null ? "" : stringEngine.process(subject, ctx);
        String b = bodyHtml == null ? "" : stringEngine.process(bodyHtml, ctx);
        return new RenderedMail(s, b);
    }

    private RenderedMail renderOf(MailTemplate tpl, Map<String, Object> model) {
        Context ctx = new Context();
        if (model != null) model.forEach(ctx::setVariable);
        return new RenderedMail(
            stringEngine.process(tpl.getSubject(), ctx),
            stringEngine.process(tpl.getBodyHtml(), ctx));
    }

    // ==================================================================
    // 내부 공통 발송
    // ==================================================================

    private void doSend(String fromEmail, String fromName, String to, String subject,
                        String html, String traceLabel) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        try {
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
            } else {
                helper.setFrom(fromEmail);
            }
        } catch (UnsupportedEncodingException ue) {
            helper.setFrom(fromEmail);
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        transport.send(message);    // Resilience4j (@Retry + @CircuitBreaker) 경유
        log.info("===MAIL_SEND ok to={} subject='{}' source={}", to, subject, traceLabel);
    }

    // ==================================================================
    // 값 객체 — 미리보기 응답
    // ==================================================================

    public record RenderedMail(String subject, String bodyHtml) {}
}
