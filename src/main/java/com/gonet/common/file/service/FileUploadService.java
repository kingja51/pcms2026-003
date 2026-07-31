package com.gonet.common.file.service;

import com.gonet.common.file.dto.UploadCommit;
import org.springframework.web.multipart.MultipartFile;

/**
 * 도메인-독립 파일 업로드 엔진 — 6중 방어 스택 전담.
 *
 * <p>어느 도메인(primary/file, 게시판, 기타) 이든 이 서비스를 주입받아 공통 파이프라인에
 * 파일을 통과시킨 뒤 결과({@link UploadCommit})를 받아 자체 DB 에 기록한다.
 * 엔진 자체는 DB 에 쓰지 않으며, 저장 경로 규칙만 일원화:
 * {@code {root}/{directory?}/{yyyy}/{MM}/{uuid}.{ext}}.
 *
 * <p>타입별 메서드:
 * <ul>
 *   <li>{@link #uploadDocument} — 문서만 (doc-extensions). 썸네일·재인코딩 없음</li>
 *   <li>{@link #uploadImage}    — 이미지만 (image-extensions). 재인코딩 + 썸네일 생성</li>
 *   <li>{@link #uploadVideo}    — 동영상만 (video-extensions). 큰 파일, 재인코딩·썸네일 skip</li>
 *   <li>{@link #uploadAny}      — allowed-extensions 전체. 관리자 범용 업로드</li>
 * </ul>
 *
 * <p>{@code directory} 파라미터는 저장 경로의 엔티티/도메인 폴더명.
 * null/blank/ETC 이면 경로에서 생략된다 ({@code {root}/yyyy/MM/...}).
 */
public interface FileUploadService {

    /** 문서 전용. pdf/hwp/hwpx/docx/xlsx/pptx/txt/csv. */
    UploadCommit uploadDocument(MultipartFile file, String directory);

    /** 이미지 전용. jpg/jpeg/png/gif/webp. 재인코딩(대용량 skip) + 400px 썸네일 자동 생성. */
    UploadCommit uploadImage(MultipartFile file, String directory);

    /**
     * 동영상 전용. mp4/mov/webm. 재인코딩·썸네일 skip — 휴대폰 대용량 파일 보존.
     * Tika 검증은 {@code video/*} MIME 만 허용.
     */
    UploadCommit uploadVideo(MultipartFile file, String directory);

    /** 카테고리 무관 — allowed-extensions 전체. 관리자 범용. */
    UploadCommit uploadAny(MultipartFile file, String directory);
}
