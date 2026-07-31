package com.gonet.primary.system.code.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 코드 목록 검색 조건 — {@link PageRequest} 상속.
 * <p>{@code codeGroupId} 는 경로 변수로 주입.
 */
@Getter
@Setter
public class CodeSearch extends PageRequest {

    private String codeGroupId;
    private String useYn;

    public CodeSearch() {
        setPageSize(100);   // 그룹 내부 코드는 한 화면에 많이 보여주는 편이 편함
    }
}
