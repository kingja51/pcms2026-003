package com.gonet.primary.board.like.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_bbs_like — 게시판 좋아요 (article + comment 통합).
 *
 * <p>UNIQUE (target_type, target_id, user_id) — 1인 1회.
 * 토글 방식 — 같은 사용자가 다시 누르면 행을 soft delete (delete_yn='Y') 로 OFF.
 */
@Getter
@Setter
public class BbsLike extends BaseEntity implements SoftDeletable {

    private String likeId;
    private String targetType;          // ARTICLE / COMMENT
    private String targetId;
    private String userId;
    private String userType;            // MEMBER / EMPLOYEE / ADMIN
    private String sourceUrl;           // 좋아요가 클릭된 페이지 URL — 감사/분석 용
    private String deleteYn;
}
