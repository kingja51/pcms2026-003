package com.gonet.primary.system.mail.service;

import com.gonet.primary.system.mail.mapper.MailTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 메일 템플릿 자동 적재기 — 앱 기동 시 1회 실행.
 *
 * <p>동작:
 * <ol>
 *   <li>DDL seed 로 만들어진 {@code tb_mail_template} 메타 행이 있지만 {@code body_html} 이
 *       NULL 인 경우, classpath 의 {@code templates/mail/*.html} 내용을 읽어 주입한다.</li>
 *   <li>관리자가 한 번이라도 편집한 템플릿(body_html 가 NOT NULL)은 건드리지 않는다
 *       — 배포 후 편집 내용 보존.</li>
 * </ol>
 *
 * <p>오류 내성: 파일 I/O 실패는 WARN 로그로 남기고 진행. 특정 템플릿 1건 실패가 전체 기동을
 * 막지 않도록 한다.
 */
@Component
public class MailTemplateSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MailTemplateSeeder.class);

    /** 코드 → classpath 경로 (확장자 포함). */
    private static final Map<String, String> DEFAULT_BODIES = new LinkedHashMap<>();
    static {
        DEFAULT_BODIES.put("MEMBER_WELCOME",   "templates/mail/member-welcome.html");
        DEFAULT_BODIES.put("PASSWORD_CHANGED", "templates/mail/password-changed.html");
        DEFAULT_BODIES.put("PASSWORD_RESET",   "templates/mail/password-reset.html");
        DEFAULT_BODIES.put("ACCOUNT_DORMANT",  "templates/mail/account-dormant.html");
        DEFAULT_BODIES.put("MEMBER_WITHDRAW",  "templates/mail/member-withdraw.html");
    }

    private final MailTemplateMapper mapper;

    public MailTemplateSeeder(MailTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Map.Entry<String, String> e : DEFAULT_BODIES.entrySet()) {
            String code = e.getKey();
            String path = e.getValue();
            try {
                if (mapper.existsBodyByCode(code) > 0) {
                    log.info("MAIL_SEED skip code={} (body already present)", code);
                    continue;
                }
                String html = readClasspath(path);
                if (html == null || html.isBlank()) {
                    log.warn("MAIL_SEED missing or empty resource code={} path={}", code, path);
                    continue;
                }
                int affected = mapper.updateBodyIfNullByCode(code, html);
                if (affected > 0) {
                    log.info("===MAIL_SEED injected code={} bytes={}", code, html.length());
                } else {
                    log.info("MAIL_SEED skipped code={} (no meta row or body already present)", code);
                }
            } catch (Exception ex) {
                log.warn("MAIL_SEED failed code={} path={} reason={}", code, path, ex.getMessage());
            }
        }
    }

    private String readClasspath(String path) {
        ClassPathResource res = new ClassPathResource(path);
        if (!res.exists()) return null;
        try (InputStream in = res.getInputStream()) {
            return FileCopyUtils.copyToString(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.warn("MAIL_SEED read-fail path={} reason={}", path, ex.getMessage());
            return null;
        }
    }
}
