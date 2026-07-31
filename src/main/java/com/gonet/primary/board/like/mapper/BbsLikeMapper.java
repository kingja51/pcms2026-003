package com.gonet.primary.board.like.mapper;

import com.gonet.primary.board.like.dto.BbsLike;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

/**
 * tb_bbs_like Mapper — 좋아요 토글 / 카운트 동기화.
 */
@EgovMapper
public interface BbsLikeMapper {

    /** 활성 좋아요 1행 조회 — 없으면 null. */
    BbsLike findActive(@Param("targetType") String targetType,
                       @Param("targetId") String targetId,
                       @Param("userId") String userId);

    /**
     * 비활성(이전에 토글 OFF 된) 좋아요 1행 조회 — soft delete 된 행 재활성화 용.
     */
    BbsLike findInactive(@Param("targetType") String targetType,
                         @Param("targetId") String targetId,
                         @Param("userId") String userId);

    int insert(BbsLike like);

    /** 활성→비활성 (delete_yn='Y'). */
    int markInactive(@Param("likeId") String likeId);

    /** 비활성→활성 (delete_yn='N'). */
    int markActive(@Param("likeId") String likeId);

    /** 활성 좋아요 수 — article/comment 의 like_count 동기화에 사용. */
    long countActive(@Param("targetType") String targetType,
                     @Param("targetId") String targetId);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
