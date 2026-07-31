package com.gonet.primary.board.comment.dto;

/**
 * 댓글 상태 — {@code tb_bbs_comment.status} CHECK 제약과 동기화.
 *
 * <ul>
 *   <li>{@link #PUBLISHED}  — 정상 노출</li>
 *   <li>{@link #HIDDEN}     — 관리자 강제 숨김 (작성자/관리자만 본문 조회)</li>
 *   <li>{@link #REPORTED}   — 신고 누적 → 보류</li>
 *   <li>{@link #DELETED}    — soft delete 마킹 (delete_yn='Y' 동반)</li>
 * </ul>
 */
public enum BbsCommentStatus {
    PUBLISHED,
    HIDDEN,
    REPORTED,
    DELETED;

    public static BbsCommentStatus safeParse(String raw) {
        if (raw == null || raw.isBlank()) return PUBLISHED;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PUBLISHED;
        }
    }
}
