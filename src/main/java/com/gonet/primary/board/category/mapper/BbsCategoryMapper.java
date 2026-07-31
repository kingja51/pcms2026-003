package com.gonet.primary.board.category.mapper;

import com.gonet.primary.board.category.dto.BbsCategory;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_bbs_category CRUD Mapper.
 *
 * <p>관리자 화면의 카테고리 CRUD + 사용자 화면의 칩/select 옵션 조회.
 */
@EgovMapper
public interface BbsCategoryMapper {

    /** 게시판의 활성 카테고리 — 글 작성 select / 사용자 목록 칩 */
    List<BbsCategory> findActiveByBbs(@Param("bbsMasterId") String bbsMasterId);

    /** 관리자 — 비활성/삭제 제외 + 글 개수 포함 */
    List<BbsCategory> findListByBbs(@Param("bbsMasterId") String bbsMasterId);

    BbsCategory findById(@Param("categoryId") String categoryId);

    /** UNIQUE 충돌 검증 — 동일 (bbs_master_id, category_code) 존재 여부 */
    int existsByCode(@Param("bbsMasterId") String bbsMasterId,
                     @Param("categoryCode") String categoryCode,
                     @Param("excludeCategoryId") String excludeCategoryId);

    int insert(BbsCategory category);

    int update(BbsCategory category);

    int updateUseYn(@Param("categoryId") String categoryId,
                    @Param("useYn") String useYn);

    int softDelete(@Param("categoryId") String categoryId);

    /** 카테고리에 속한 글 수 — 삭제 가드 */
    int countActiveArticles(@Param("categoryId") String categoryId);

    // ------------------------------------------------------------------
    // Soft-delete retention
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
