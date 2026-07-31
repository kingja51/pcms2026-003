package com.gonet.primary.admin.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 그룹 목록 검색 조건 — {@link PageRequest} 상속.
 */
@Getter
@Setter
public class AdminGroupSearch extends PageRequest {

    private String useYn;

    public AdminGroupSearch() {
        setPageSize(50);
    }
}
