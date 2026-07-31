package com.gonet.primary.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 파일 업로드 요청 메타데이터 (REST) — multipart/form-data 의 비-파일 필드.
 *
 * <p>실제 파일은 {@code MultipartFile} 파라미터로 분리 전달.
 */
@Schema(description = "파일 업로드 메타데이터 — multipart/form-data 의 비-파일 부분")
@Getter
@Setter
public class UploadRequest {

    @Schema(description = "상위 엔티티 타입 — BBS(게시판)/CNT(콘텐츠)/MBR(회원)/POPUP/BANNER/MAIL/GEMINI_SEARCH/ETC",
        example = "BBS",
        allowableValues = {"BBS","CNT","MBR","POPUP","BANNER","MAIL","GEMINI_SEARCH","ETC"},
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Pattern(regexp = "^(BBS|CNT|MBR|POPUP|BANNER|MAIL|GEMINI_SEARCH|ETC)$",
             message = "entityType 은 BBS/CNT/MBR/POPUP/BANNER/MAIL/GEMINI_SEARCH/ETC 중 하나")
    private String entityType;

    @Schema(description = "상위 엔티티 ID (UUID v7). 미지정 시 ETC 그룹 자동 생성.",
        example = "019d9b80-0b79-780c-be2a-264a22b14848", maxLength = 40)
    @Size(max = 40)
    private String entityId;

    @Schema(description = "사이트 ID (선택) — 멀티사이트 구분용. EMPTY 폴백 환경에서는 null 허용.",
        maxLength = 40)
    @Size(max = 40)
    private String siteId;

    @Schema(description = "업로드 순서 — file-picker 가 그룹 내 기존 파일 수 + 클라 큐 인덱스. " +
        "Service 가 tb_file.sort_order 컬럼에 저장. 미전송 시 0.",
        example = "0")
    private Integer sortOrder;
}
