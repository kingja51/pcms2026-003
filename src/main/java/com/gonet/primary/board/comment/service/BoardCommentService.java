package com.gonet.primary.board.comment.service;

import com.gonet.primary.board.comment.dto.BbsComment;
import com.gonet.primary.board.comment.dto.BbsCommentSaveForm;

import java.util.List;

/**
 * 댓글 서비스 — 회원/관리자 공통 진입점.
 *
 * <p>책임:
 * <ul>
 *   <li>댓글 작성 시 게시판 정책 (comment_yn='Y') 검증</li>
 *   <li>비밀글 댓글은 작성자/관리자만 본문 조회 가능 (호출 측에서 컨텍스트 전달)</li>
 *   <li>article.comment_count 자동 동기화 (PUBLISHED 만 카운트)</li>
 *   <li>감사 이벤트 발행 (BBS_COMMENT_CREATE/UPDATE/DELETE/STATUS)</li>
 * </ul>
 */
public interface BoardCommentService {

    /** 게시글의 모든 활성 댓글 (스레드 정렬). 비밀글 처리는 호출 측이. */
    List<BbsComment> listByArticle(String articleId);

    BbsComment get(String commentId);

    /** @return 신규 comment_id (UUID v7). */
    String create(BbsCommentSaveForm form, String clientIp);

    /** 본인/관리자 수정 — content 만 변경. */
    void update(BbsCommentSaveForm form, String clientIp);

    /** 본인/관리자 soft delete. */
    void softDelete(String commentId);

    /** 관리자 강제 상태 전환. */
    void adminUpdateStatus(String commentId, String status);

    /**
     * 회원 대시보드 — 본인이 작성한 최신 댓글.
     * created_by = userId AND delete_yn='N' AND status &lt;&gt; 'DELETED'.
     */
    List<BbsComment> findRecentByCreatedBy(String userId, int limit);
}
