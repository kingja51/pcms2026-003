package com.gonet.primary.board.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 신고 등록 폼 — REST API 요청 바디.
 *
 * <p>{@code targetType / targetId} 는 URL path 에서 받으므로 바디에는 제외.
 * {@code reasonCode} 와 {@code reasonText} 만 검증.
 */
@Getter
@Setter
public class BbsReportSaveForm {

    @NotBlank(message = "신고 사유 코드는 필수입니다.")
    @Pattern(regexp = "^(SPAM|OFFENSIVE|ILLEGAL|COPYRIGHT|PRIVACY|OTHER)$",
             message = "신고 사유 코드가 올바르지 않습니다.")
    private String reasonCode;

    @Size(max = 1000, message = "추가 설명은 1000자 이내")
    private String reasonText;

    /** 신고가 접수된 페이지 URL — 감사용. */
    @Size(max = 1000)
    private String sourceUrl;
}
