package com.gonet.primary.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComplaintAnswerSaveForm {

    @NotBlank
    private String articleId;

    @NotBlank
    private String content;

    /** file-picker hidden input */
    private String attachmentsJson;

    /** Y: 최종 답변 → article.status = ANSWERED 자동 전환 */
    private String isFinal = "N";
}
