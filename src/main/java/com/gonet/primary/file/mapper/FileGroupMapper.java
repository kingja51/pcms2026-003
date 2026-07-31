package com.gonet.primary.file.mapper;

import com.gonet.primary.file.dto.FileGroup;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * tb_file_group CRUD Mapper.
 *
 * <p>그룹은 업로드 시 자동 생성 — 관리자 화면에서 직접 CRUD 하지 않는다 (tb_file 경유).
 */
@EgovMapper
public interface FileGroupMapper {

    FileGroup findById(@Param("fileGroupId") String fileGroupId);

    /** 동일 엔티티에 이미 그룹이 있으면 재사용 — UPSERT 전 조회. */
    FileGroup findByEntity(@Param("entityType") String entityType,
                            @Param("entityId") String entityId);

    int insert(FileGroup group);

    /** download_auth 만 갱신 — 게시판 master.read_auth 변경 시 일괄 동기화 등에 사용. */
    int updateDownloadAuth(@Param("fileGroupId") String fileGroupId,
                           @Param("downloadAuth") String downloadAuth,
                           @Param("updatedBy") String updatedBy,
                           @Param("updatedIp") String updatedIp);

    /**
     * 게시판 마스터 download_auth 변경 시 — 해당 게시판의 모든 article 의 file_group 에 cascade.
     * tb_bbs_article 을 JOIN 하여 entity_type='BBS' AND article.bbs_master_id 매치하는 그룹 일괄 UPDATE.
     *
     * @return 업데이트된 file_group 행수
     */
    int cascadeUpdateDownloadAuthByBbs(@Param("bbsMasterId") String bbsMasterId,
                                        @Param("downloadAuth") String downloadAuth,
                                        @Param("updatedBy") String updatedBy,
                                        @Param("updatedIp") String updatedIp);

    int softDelete(@Param("fileGroupId") String fileGroupId);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 를 계산해 전달
    // ------------------------------------------------------------------

    /** cutoff 이전 soft-deleted (delete_yn='Y') 행 hard delete. */
    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** dry-run 후보 카운트 — DELETE 없이. */
    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
