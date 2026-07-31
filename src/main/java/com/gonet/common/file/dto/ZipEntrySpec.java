package com.gonet.common.file.dto;

/**
 * ZIP 다운로드 entry 스펙 — {@code FileDownloadService.downloadZip()} 인자.
 *
 * @param storedPath 정식 저장소 root 기준 상대 경로 (tb_file.stored_path)
 * @param entryName  zip 내부에 표시될 파일명 (보통 원본 파일명)
 */
public record ZipEntrySpec(String storedPath, String entryName) {}
