package com.gonet.primary.board.master.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시판 마스터 목록 검색 조건.
 *
 * <p>필터:
 * <ul>
 *   <li>{@code siteId} — 사이트 한정 (드롭다운)</li>
 *   <li>{@code bbsType} — 타입 필터 (NOTICE/FREE/.../ALL)</li>
 *   <li>{@code useYn} — 사용중지 포함 여부 (ALL 이면 무관)</li>
 *   <li>{@code keyword} — bbs_name / bbs_code LIKE 검색 ({@link PageRequest})</li>
 * </ul>
 */
@Getter
@Setter
public class BbsMasterSearch extends PageRequest {

    private String siteId;
    private String bbsType;
    private String useYn;
}
