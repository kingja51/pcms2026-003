package com.gonet.primary.board.master.dto;

/**
 * 게시판 타입 — {@code tb_bbs_master.bbs_type} CHECK 제약과 동기화.
 *
 * <p>설계서 §6.5 의 5종 게시판 + FILE 타입(자료실).
 *
 * <ul>
 *   <li>{@link #NOTICE}  — 공지: 상단 고정 + 관리자 작성</li>
 *   <li>{@link #BODO}    — 보도자료: 관리자 글 + 외부 언론사명/링크 메타</li>
 *   <li>{@link #FREE}    — 자유: 회원 일반 글</li>
 *   <li>{@link #FAQ}     — 질의응답 카탈로그 (작성/답변 분리)</li>
 *   <li>{@link #QNA}     — 1:1 문의 (secret 기본)</li>
 *   <li>{@link #GALLERY} — 이미지 그리드 표시</li>
 *   <li>{@link #FILE}    — 자료실 (파일 첨부 중심)</li>
 *   <li>{@link #PDF}    — PDF뷰어 (파일 첨부 중심)</li>
 *   <li>{@link #YOUTUBE} — 유튜브 (link_url + HD 썸네일 + iframe 재생)</li>
 * </ul>
 */
public enum BbsType {
    NOTICE("공지"),
    BODO("보도자료"),
    FREE("자유"),
    FAQ("FAQ"),
    QNA("Q&A"),
    GALLERY("갤러리"),
    FILE("자료실"),
    PDF("PDF뷰어"),
    YOUTUBE("유튜브");

    private final String label;

    BbsType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 안전 파싱 — 알 수 없으면 {@link #FREE} 반환. */
    public static BbsType safeParse(String raw) {
        if (raw == null || raw.isBlank()) return FREE;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FREE;
        }
    }
}
