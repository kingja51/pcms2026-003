package com.gonet.primary.system.department.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * tb_department 경량 DTO — 직원 폼의 부서 드롭다운 소스.
 *
 * <p>{@code depth} 를 사용해 트리 들여쓰기 표시를 할 수 있도록 포함 (선택적 UI 활용).
 */
@Getter
@Setter
public class DepartmentOption {

    private String  departmentId;
    private String  departmentCode;
    private String  departmentName;
    private Integer depth;
    private String  parentDepartmentId;

    /** depth 기반 들여쓰기 + 이름 — 폼 드롭다운 표시용. depth=1 이면 들여쓰기 없음. */
    public String getIndentedName() {
        int d = depth == null ? 1 : Math.max(1, depth);
        if (d == 1) return departmentName;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < d; i++) sb.append("  ");
        sb.append("└ ").append(departmentName);
        return sb.toString();
    }
}
