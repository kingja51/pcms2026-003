package com.gonet.primary.board.comment.mapper;

import com.gonet.primary.board.comment.dto.BbsComment;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_bbs_comment CRUD Mapper.
 *
 * <p>설계 결정:
 * <ul>
 *   <li>스레드 정렬 — 상위 root(parent IS NULL) 정렬 후 그 자식을 root 바로 뒤에 끼워넣는 방식.
 *       단순히 (COALESCE(parent_id, id), created_at) 으로 정렬하면 평탄한 흐름은 잡히지만
 *       대댓글이 상위보다 먼저 나올 수 있음. 본 구현은 root 단위 SUBSELECT 로 정렬키를 구성</li>
 *   <li>HIDDEN/REPORTED 댓글도 목록에는 포함 — 본문은 화면에서 가린다 (상태별 표시)</li>
 *   <li>관리자 모더레이션은 articleId 무관하게 commentId 단건으로 가능</li>
 * </ul>
 */
@EgovMapper
public interface BbsCommentMapper {

    /** 게시글의 모든 활성 댓글을 스레드 정렬로 반환. soft-delete 만 제외. */
    List<BbsComment> findByArticleId(@Param("articleId") String articleId);

    int countByArticleId(@Param("articleId") String articleId);

    BbsComment findById(@Param("commentId") String commentId);

    int insert(BbsComment comment);

    int update(BbsComment comment);

    /** soft delete + status='DELETED'. */
    int softDelete(@Param("commentId") String commentId);

    /** 관리자 강제 상태 전환. */
    int updateStatus(@Param("commentId") String commentId,
                      @Param("status") String status);

    /** like_count 절대값 설정 — reset(0)/전체 재계산 전용. 토글은 {@link #incrementLikeCount}. */
    int updateLikeCount(@Param("commentId") String commentId,
                         @Param("likeCount") long likeCount);

    /** report_count 절대값 설정 — reset 전용. 신고 적재는 {@link #incrementReportCount}. */
    int updateReportCount(@Param("commentId") String commentId,
                           @Param("reportCount") long reportCount);

    /** like_count 원자적 증감 (H-1 race 가드). delta=+1/-1. */
    int incrementLikeCount(@Param("commentId") String commentId,
                            @Param("delta") int delta);

    /** report_count 원자적 증감 (H-1 race 가드). delta=+1. */
    int incrementReportCount(@Param("commentId") String commentId,
                              @Param("delta") int delta);

    /** 신고 누적 임계 도달 시 PUBLISHED → REPORTED 자동 전환. */
    int markStatusReportedIfPublished(@Param("commentId") String commentId);

    /**
     * 회원 대시보드 — 본인이 작성한 최신 댓글.
     * created_by = userId AND delete_yn='N' AND status &lt;&gt; 'DELETED'.
     * JOIN article + master + site 로 댓글이 달린 게시글 정보 동시 노출.
     */
    List<BbsComment> findRecentByCreatedBy(@Param("createdBy") String createdBy,
                                            @Param("limit") int limit);

    /**
     * Soft-delete retention — cutoff 이전 (updated_at &lt;= cutoff) 의
     * delete_yn='Y' 행을 hard delete. 호출 측에서 도메인별 트랜잭션을 연다.
     */
    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** Soft-delete retention — dry-run 카운트. DELETE 없이 후보 행 수만. */
    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
