package com.gonet.primary.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComplaintArticleSaveForm {

    @NotBlank
    private String complaintMasterId;

    /** writeForm GET 단계에서 사전 발급된 UUID — file-picker entityId 로 사용 */
    private String articleId;

    private String categoryId;

    // 작성자 — Controller 에서 인증 정보 기반으로 채움
    private String writerAuthType;
    private String writerMemberId;
    private String writerDiHash;
    private String writerOauthProvider;
    private String writerOauthUidHash;

    @NotBlank @Size(max = 100)
    private String writerName;

    @Size(max = 200)
    private String writerContact;    // 평문 입력 → Service 에서 AES-GCM 암호화

    @NotBlank @Size(max = 300)
    private String title;

    @NotBlank
    private String content;

    /** file-picker hidden input — JSON UUID 배열 */
    private String attachmentsJson;
}
