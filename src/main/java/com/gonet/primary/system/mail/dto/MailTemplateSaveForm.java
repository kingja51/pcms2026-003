package com.gonet.primary.system.mail.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 메일 템플릿 등록/수정 폼.
 *
 * <p>생성 시: {@code templateCode} 는 UPPER_SNAKE_CASE (시스템 참조용 — 변경 제한 권장).
 * <p>수정 시: {@code templateCode} 는 읽기전용으로 UI 에서 잠금.
 */
@Getter
@Setter
public class MailTemplateSaveForm {

    private String mailTemplateId;       // 수정 시 hidden

    @NotBlank @Size(max = 50)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]+$",
             message = "템플릿 코드는 대문자/숫자/언더스코어만 (첫 글자 대문자)")
    private String templateCode;

    @NotBlank @Size(max = 100)
    private String templateName;

    @NotBlank @Size(max = 500)
    private String subject;

    @NotBlank
    private String bodyHtml;             // MEDIUMTEXT — 상한 DB 레벨 검증

    @Email @Size(max = 255)
    private String senderEmail;

    @Size(max = 100)
    private String senderName;

    @Size(max = 1000)
    private String description;

    @Size(max = 2000)
    private String variablesHint;

    private String useYn = "Y";
}
