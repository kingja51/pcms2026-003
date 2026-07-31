package com.gonet.primary.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 파일 업로드 REST 응답 — 6중 방어 스택 통과 후 저장된 파일 요약.
 *
 * <p>{@link com.gonet.common.dto.ApiResponse} 로 감싸서 반환:
 * {@code ApiResponse<UploadResult>}.
 */
@Schema(description = "업로드 성공 응답 — 6중 방어 스택(확장자 화이트리스트 / Tika 매직바이트 / " +
    "이미지 재인코딩 / SHA-256 FIM / 격리 저장소 / ClamAV 큐) 통과 후 저장된 파일 요약.")
@Getter
public class UploadResult {

    @Schema(description = "파일 ID (UUID v7) — 다운로드 경로 키",
        example = "019d9b80-0b79-780c-be2a-264a22b14848")
    private final String fileId;

    @Schema(description = "파일 그룹 ID — 같은 엔티티에 속한 파일들의 묶음",
        example = "019d9b80-0b79-780c-be2a-264a22b14848")
    private final String fileGroupId;

    @Schema(description = "사용자가 업로드한 원본 파일명 (sanitize 후)", example = "report.pdf")
    private final String originalName;

    @Schema(description = "확장자 (소문자, dot 제외)", example = "pdf")
    private final String extension;

    @Schema(description = "Tika 가 매직바이트로 판정한 실제 MIME — 확장자/MIME 불일치 시 업로드 차단됨",
        example = "application/pdf")
    private final String mimeDetected;

    @Schema(description = "저장된 파일 크기(byte)", example = "524288")
    private final long   sizeBytes;

    @Schema(description = "SHA-256 hex — FIM(File Integrity Monitoring) 무결성 키",
        example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    private final String fileHash;

    @Schema(description = "이미지 재인코딩 여부 — true 면 Thumbnailator 로 폴리글랏 공격 방어 적용",
        example = "true")
    private final boolean imageReencoded;

    @Schema(description = "ClamAV 검사 상태 — `PENDING`(대기/NoOp) / `CLEAN` / `INFECTED` / `ERROR`. " +
        "`PENDING`/`CLEAN` 만 다운로드 허용.")
    private final VirusScanStatus virusScanStatus;

    public UploadResult(String fileId, String fileGroupId, String originalName,
                        String extension, String mimeDetected, long sizeBytes,
                        String fileHash, boolean imageReencoded,
                        VirusScanStatus virusScanStatus) {
        this.fileId          = fileId;
        this.fileGroupId     = fileGroupId;
        this.originalName    = originalName;
        this.extension       = extension;
        this.mimeDetected    = mimeDetected;
        this.sizeBytes       = sizeBytes;
        this.fileHash        = fileHash;
        this.imageReencoded  = imageReencoded;
        this.virusScanStatus = virusScanStatus;
    }
}
