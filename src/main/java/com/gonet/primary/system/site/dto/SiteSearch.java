package com.gonet.primary.system.site.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 사이트 목록 검색 조건 — {@link PageRequest} 상속.
 */
@Getter
@Setter
public class SiteSearch extends PageRequest {

    /** 'Y' / 'N' / null(전체) */
    private String useYn;
}
