package com.gonet.primary.system.code.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 코드 그룹 목록 검색 조건 — {@link PageRequest} 상속.
 */
@Getter
@Setter
public class CodeGroupSearch extends PageRequest {

    private String useYn;

    public CodeGroupSearch() {
        setPageSize(50);
    }
}
