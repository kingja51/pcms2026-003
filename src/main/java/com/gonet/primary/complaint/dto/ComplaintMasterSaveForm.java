package com.gonet.primary.complaint.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComplaintMasterSaveForm {

    private String  complaintMasterId;   // 수정 시 세팅

    @NotBlank
    private String  siteId;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,49}$",
             message = "영문 시작, 영문/숫자/언더스코어/하이픈 2~50자")
    private String  masterCode;

    @NotBlank @Size(max = 100)
    private String  masterName;

    @Size(max = 500)
    private String  description;

    private String  menuId;

    private String  noticeHtml;

    // 작성 인증 정책 (최소 1개 Y 검증은 Service)
    private String  allowMemberYn  = "Y";
    private String  allowOauth2Yn  = "Y";
    private String  allowNiceYn    = "Y";

    /** CAPTCHA 사용 여부 — 체크박스. null/미체크 시 'N'. Y 이면 글쓰기 시 reCAPTCHA v3 강제. 민원 도메인 권장 'Y'. */
    private String  captchaYn      = "Y";

    // 목록 노출 정책
    private String  showInList     = "N";

    // 첨부파일 정책
    private String  fileYn         = "Y";

    @Min(1)
    private int     fileCountMax   = 5;

    /** MB 단위 입력 → Service 에서 byte 환산 */
    @Min(1)
    private int     fileSizeMb     = 10;

    // 답변 정책
    private String  answerRequiredYn = "N";
    private Integer answerDeadlineDays;

    private String  useYn = "Y";
}
