package com.gonet.primary.board.like.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 좋아요 토글 응답 DTO.
 *
 * <p>{@code liked} = 토글 후 사용자의 좋아요 상태 (true=눌린 상태).
 * {@code likeCount} = 토글 후 대상의 누적 좋아요 수.
 */
@Getter
@RequiredArgsConstructor
public class LikeToggleResult {
    private final boolean liked;
    private final long    likeCount;
}
