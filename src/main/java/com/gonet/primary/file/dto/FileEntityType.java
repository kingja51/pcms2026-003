package com.gonet.primary.file.dto;

/**
 * 파일 그룹 entity_type — {@code tb_file_group.entity_type}.
 *
 * <p>파일이 속한 상위 엔티티 종류. 확장 시 DDL 변경 없이 상수 추가.
 */
public enum FileEntityType {

    /** 게시판 글 첨부. */
    BBS,
    /** 콘텐츠 첨부 (이미지 등). */
    CNT,
    /** 회원 프로필/증빙. */
    MBR,
    /** 팝업/배너 이미지. */
    POPUP,
    /** 배너/배너 이미지. */
    BANNER,
    /** 메일 첨부. */
    MAIL,
    /** Gemini Files API 문서검색 RAG — tb_search_gemini_file 연결. */
    GEMINI_SEARCH,
    /** 기타/임시 — 최종 귀속 전. */
    ETC;

    public static FileEntityType safeParse(String raw) {
        if (raw == null || raw.isBlank()) return ETC;
        try {
            return FileEntityType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ETC;
        }
    }
}
