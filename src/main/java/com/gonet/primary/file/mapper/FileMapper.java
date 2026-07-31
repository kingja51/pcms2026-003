package com.gonet.primary.file.mapper;

import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_file CRUD Mapper — 관리자 조회·soft delete + 업로드 INSERT.
 */
@EgovMapper
public interface FileMapper {

    List<FileItem> findList(@Param("search") FileSearch search);

    int countList(@Param("search") FileSearch search);

    FileItem findById(@Param("fileId") String fileId);

    /**
     * 그룹 zip 다운로드용 — 해당 그룹의 다운로드 가능(CLEAN) 파일을 sort_order 순으로.
     * soft-deleted/PENDING/INFECTED/ERROR 는 제외.
     */
    List<FileItem> findDownloadableByGroupId(@Param("fileGroupId") String fileGroupId);

    /**
     * 게시글 작성/수정 화면 노출용 — soft-deleted 만 제외하고 PENDING/CLEAN/ERROR 모두 반환.
     * 작성자가 첨부 직후 본인 파일을 시각화하기 위해 virus_scan_status 무관 노출.
     * INFECTED 는 별도 정책 — 노출 자체는 가능하지만 다운로드는 차단됨.
     */
    List<FileItem> findAllByGroupId(@Param("fileGroupId") String fileGroupId);

    /** SHA-256 중복 검사 — 동일 entity_id 범위 내 (기존 파일 hash 와 비교). */
    int countByHash(@Param("fileGroupId") String fileGroupId,
                     @Param("fileHash") String fileHash);

    int insert(FileItem file);

    int softDelete(@Param("fileId") String fileId);

    /** ClamAV 검사 결과 반영 — scheduled worker 가 호출. */
    int updateVirusScanStatus(@Param("fileId") String fileId,
                               @Param("status") String status);

    /**
     * PENDING 재스캔 대상 — {@code virus_scan_status='PENDING'} + {@code updated_at &lt; cutoff}.
     * 서버 재시작·executor 유실 복구용. 방금 업로드된 파일은 {@code cutoff} 기준으로 제외하여
     * 중복 스캔 방지.
     */
    List<FileItem> findPendingForRescan(@Param("cutoff") LocalDateTime cutoff,
                                         @Param("limit") int limit);

    /** 비동기 썸네일 생성 완료 후 경로 반영. */
    int updateThumbnailPath(@Param("fileId") String fileId,
                             @Param("thumbnailPath") String thumbnailPath);

    int incrementDownloadCount(@Param("fileId") String fileId);

    // ------------------------------------------------------------------
    // Purge batch — delete_yn='Y' + updated_at < cutoff
    // ------------------------------------------------------------------

    /**
     * 물리 삭제 대상 목록 — {@code delete_yn='Y'} + {@code updated_at < cutoff},
     * 오래된 순. batchSize 로 제한해 한 번에 처리할 수 있는 양만.
     */
    List<FileItem> findPurgeCandidates(@Param("cutoff") LocalDateTime cutoff,
                                         @Param("limit") int limit);

    /** DB 행 HARD DELETE — 물리 파일 삭제 성공 후 호출. */
    int hardDelete(@Param("fileId") String fileId);

    /**
     * 색인 재구축용 — 활성 파일 전체 (group 의 entity_type/entity_id/site_id 노출).
     * Service 가 INFECTED 는 색인 제외, 그 외(CLEAN/PENDING/ERROR)는 색인.
     */
    List<FileItem> findAllForReindex();

    /**
     * Soft-delete retention — cutoff 이전 (updated_at &lt;= cutoff) 의
     * delete_yn='Y' 행을 hard delete. 호출 측에서 도메인별 트랜잭션을 연다.
     */
    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** Soft-delete retention — dry-run 카운트. DELETE 없이 후보 행 수만. */
    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
