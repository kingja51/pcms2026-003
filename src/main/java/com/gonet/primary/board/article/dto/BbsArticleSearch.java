package com.gonet.primary.board.article.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시글 목록 검색 조건.
 *
 * <p>필터:
 * <ul>
 *   <li>{@code bbsMasterId} — 게시판 한정 (관리자/사용자 화면 모두 필수)</li>
 *   <li>{@code status} — 상태 필터 (PUBLISHED/HIDDEN/REPORTED/DELETED/ALL).
 *       사용자 화면에서는 강제 PUBLISHED, 관리자 화면에서만 옵션 노출</li>
 *   <li>{@code searchType} — TITLE / WRITER / CONTENT / ALL (기본 ALL — title + writer_name)</li>
 *   <li>{@code keyword} — LIKE 검색 (PageRequest)</li>
 *   <li>{@code includeNotice} — 공지 글 포함 여부 (기본 true). 관리자 화면 별도 노출용</li>
 * </ul>
 */
@Getter
@Setter
public class BbsArticleSearch extends PageRequest {

    private String  bbsMasterId;
    private String  status;
    private String  categoryId;
    private boolean includeNotice = true;
    private String[] groupedBoardIdArray;     // 통합 게시판 모드. 구분자 콤마
}
