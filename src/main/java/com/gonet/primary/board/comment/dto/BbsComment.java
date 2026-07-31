package com.gonet.primary.board.comment.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_bbs_comment 엔티티 — 게시글 댓글.
 *
 * <p>관계:
 * <ul>
 *   <li>{@code article_id} → tb_bbs_article (FK)</li>
 *   <li>{@code parent_comment_id} → tb_bbs_comment (self) — 대댓글 (NULL 이면 최상위)</li>
 * </ul>
 *
 * <p>{@code depth} 는 정규화된 깊이 — 1=top, 2=reply. UI 는 2 단계까지만 평면 표시.
 * 더 깊은 reply 는 일단 같은 부모로 평탄화 (B3 단계).
 */
@Getter
@Setter
public class BbsComment extends BaseEntity implements SoftDeletable {

    private String commentId;
    private String articleId;
    private String parentCommentId;

    private String  writerUserId;
    private String  writerUserType;
    private String  writerName;

    private String  content;
    private String  secretYn;
    private long    likeCount;
    private long    reportCount;
    private int     depth;
    private String  clientIp;
    private String  status;
    private String  deleteYn;

    /** 대시보드 JOIN 필드 — 댓글이 달린 게시글의 식별 정보. */
    private String  articleTitle;
    private String  bbsCode;
    private String  bbsName;
    private String  siteCode;

    public BbsCommentStatus statusEnum() {
        return BbsCommentStatus.safeParse(status);
    }

    public boolean isReply() {
        return parentCommentId != null && !parentCommentId.isBlank();
    }

    /** 비밀 댓글 여부. */
    public boolean isSecret() {
        return "Y".equalsIgnoreCase(secretYn);
    }
}
