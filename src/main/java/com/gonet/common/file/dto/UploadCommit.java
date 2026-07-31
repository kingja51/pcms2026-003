package com.gonet.common.file.dto;

import lombok.Getter;

/**
 * 6중 방어 스택 통과 후 파일 엔진이 호출 측에 돌려주는 저장 결과 스냅샷.
 *
 * <p>호출 측(primary 도메인/게시판/기타)은 이 값을 받아 자체 DB 테이블에 기록한다.
 * 엔진 자체는 DB 에 쓰지 않아 재사용성이 높다.
 */
@Getter
public class UploadCommit {

    /** 업로드 원본 파일명 (사용자가 보낸 값). */
    private final String  originalName;
    /** 서버 저장 파일명 (UUID + 확장자). */
    private final String  storedName;
    /** 정식 저장소(root) 기준 상대 경로 — {@code {TYPE?}/{yyyy}/{MM}/{stored}}. */
    private final String  storedPath;
    /** 썸네일 저장소 기준 상대 경로. 이미지가 아니거나 생성 skip 시 null. */
    private final String  thumbnailPath;
    /** 정규화된 소문자 확장자. */
    private final String  extension;
    /** Tika 매직바이트 검출 MIME. */
    private final String  mimeDetected;
    /** 클라이언트 헤더 Content-Type (참고용, 신뢰 금지). */
    private final String  mimeClient;
    /** 최종 파일 크기 (재인코딩 반영 후). */
    private final long    sizeBytes;
    /** SHA-256 소문자 64자. */
    private final String  fileHash;
    /** Tika 기준 이미지 여부. */
    private final boolean image;
    /** Thumbnailator 재인코딩 적용 여부. */
    private final boolean reencoded;
    /** 카테고리 — 호출 경로 구분용. */
    private final UploadCategory category;

    public UploadCommit(String originalName, String storedName, String storedPath,
                        String thumbnailPath, String extension, String mimeDetected,
                        String mimeClient, long sizeBytes, String fileHash,
                        boolean image, boolean reencoded, UploadCategory category) {
        this.originalName  = originalName;
        this.storedName    = storedName;
        this.storedPath    = storedPath;
        this.thumbnailPath = thumbnailPath;
        this.extension     = extension;
        this.mimeDetected  = mimeDetected;
        this.mimeClient    = mimeClient;
        this.sizeBytes     = sizeBytes;
        this.fileHash      = fileHash;
        this.image         = image;
        this.reencoded     = reencoded;
        this.category      = category;
    }
}
