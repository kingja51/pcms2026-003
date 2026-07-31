package com.gonet.common.file.dto;

/**
 * 업로드 카테고리 — {@code FileUploadService} 가 확장자 검증 범위·후처리 정책을 결정.
 *
 * <pre>
 *   DOC    — 문서만 허용 (pdf/hwp/hwpx/docx/xlsx/pptx/txt/csv)
 *   IMAGE  — 이미지만 허용 (jpg/jpeg/png/gif/webp) → 재인코딩 + 썸네일
 *   VIDEO  — 동영상만 허용 (mp4/mov/webm) → 재인코딩 skip, 썸네일 skip (큰 파일)
 *   ANY    — allowed-extensions 전체 (관리자 범용 업로드)
 * </pre>
 */
public enum UploadCategory {
    DOC,
    IMAGE,
    VIDEO,
    ANY
}
