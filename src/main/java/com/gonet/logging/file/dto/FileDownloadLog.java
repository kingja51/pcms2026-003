package com.gonet.logging.file.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * log_file_download — 파일 다운로드 이력 1건.
 *
 * <p>PK 는 BIGINT AUTO_INCREMENT — logging DB 규약. 파일 삭제 후에도 이력 조회
 * 가능하도록 original_name / extension / size_bytes 를 스냅샷.
 */
@Getter
@Setter
public class FileDownloadLog {

    private Long   logFileDownloadId;

    private String fileId;
    private String fileGroupId;
    private String downloadType;     // SINGLE / GROUP_ZIP / ADMIN

    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;

    private String originalName;
    private String extension;
    private Long   sizeBytes;

    private String requestUri;
    private String clientIp;
    private String userAgent;
    private String traceId;

    private String result;            // SUCCESS / FAIL / BLOCKED

    private LocalDateTime downloadedAt;

    // 6감사컬럼
    private String createdBy;
    private String createdIp;
    private LocalDateTime createdAt;
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedAt;
}
