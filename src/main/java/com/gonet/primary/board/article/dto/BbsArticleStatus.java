package com.gonet.primary.board.article.dto;

/**
 * 게시글 상태 — {@code tb_bbs_article.status} CHECK 제약과 동기화.
 *
 * <ul>
 *   <li>{@link #PUBLISHED}  — 정상 게시 (사용자 노출)</li>
 *   <li>{@link #HIDDEN}     — 관리자가 강제 숨김 (작성자/관리자만 조회)</li>
 *   <li>{@link #REPORTED}   — 신고 누적 → 자동/관리자 보류 (관리자 화면만 노출)</li>
 *   <li>{@link #DELETED}    — soft delete 마킹 — soft delete 는 {@code delete_yn='Y'} 도 함께 세팅</li>
 * </ul>
 */
public enum BbsArticleStatus {
    PUBLISHED,
    HIDDEN,
    REPORTED,
    DELETED;

    public static BbsArticleStatus safeParse(String raw) {
        if (raw == null || raw.isBlank()) return PUBLISHED;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PUBLISHED;
        }
    }
}
