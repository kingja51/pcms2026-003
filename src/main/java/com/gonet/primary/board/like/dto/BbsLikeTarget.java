package com.gonet.primary.board.like.dto;

/**
 * 좋아요/신고 대상 종류.
 *
 * <p>다형 키 — `tb_bbs_like.target_type` / `tb_bbs_report.target_type` 에 저장.
 * <ul>
 *   <li>ARTICLE  — tb_bbs_article.article_id</li>
 *   <li>COMMENT  — tb_bbs_comment.comment_id</li>
 *   <li>CONTENT  — tb_content.content_id (사이트별 정적 콘텐츠 페이지)</li>
 * </ul>
 *
 * <p>CONTENT 는 자동 REPORTED 상태 전환 흐름이 없음 — 신고만 적재되고 관리자 검토 큐에 노출.
 */
public enum BbsLikeTarget {
    ARTICLE,
    COMMENT,
    CONTENT;

    /** 안전 파싱 — 알 수 없으면 null. */
    public static BbsLikeTarget safeParse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
