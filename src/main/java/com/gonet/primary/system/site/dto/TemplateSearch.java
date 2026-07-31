package com.gonet.primary.system.site.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 템플릿 목록 검색 조건 + 페이징 — {@link PageRequest} 상속.
 *
 * <p>2026-04-23c 부터 템플릿은 전역이므로 {@code siteId} 필드 제거.
 */
@Getter
@Setter
public class TemplateSearch extends PageRequest {

    private String useYn;      // 'Y' / 'N' / null(전체)
}
