package com.gonet.primary.content.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 콘텐츠 목록 검색 조건.
 *
 * <p>필터:
 * <ul>
 *   <li>{@code siteId} — 사이트 한정 (드롭다운)</li>
 *   <li>{@code status} — 상태 필터 (DRAFT/REVIEW/APPROVED/PUBLISHED/UNPUBLISHED/ALL)</li>
 *   <li>{@code searchType} — TITLE / SLUG / BODY / ALL (기본 ALL)</li>
 *   <li>{@code keyword} — 검색 키워드 (LIKE)</li>
 * </ul>
 */
@Getter
@Setter
public class ContentSearch extends PageRequest {

    private String siteId;
    private String status;
}
