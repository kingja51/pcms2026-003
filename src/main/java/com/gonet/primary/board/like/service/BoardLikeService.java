package com.gonet.primary.board.like.service;

import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.like.dto.LikeToggleResult;
import com.gonet.primary.system.login.dto.CustomUserDetails;

/**
 * 게시판 좋아요 토글 — article + comment 통합.
 *
 * <p>비로그인 사용자는 {@link org.springframework.security.access.AccessDeniedException}.
 * 같은 사용자가 다시 호출하면 OFF (soft delete) 로 토글.
 */
public interface BoardLikeService {

    /**
     * 좋아요 토글 — 활성이면 OFF, 비활성/없음이면 ON.
     *
     * @param target ARTICLE 또는 COMMENT
     * @param targetId 대상 ID (article_id 또는 comment_id)
     * @param sourceUrl 좋아요가 클릭된 페이지 URL — 감사용. 신규 INSERT 시에만 저장 (토글 ON/OFF 시 갱신 안 함)
     * @param me 현재 사용자 (null 이면 AccessDenied)
     * @return 토글 후 상태 + 누적 좋아요 수
     */
    LikeToggleResult toggle(BbsLikeTarget target, String targetId, String sourceUrl, CustomUserDetails me);

    /**
     * 현재 사용자가 좋아요를 눌렀는지 여부 — 사용자 화면에서 버튼 활성 표시용.
     * 비로그인이면 false.
     */
    boolean isLikedBy(BbsLikeTarget target, String targetId, CustomUserDetails me);
}
