package com.gonet.primary.system.department.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 부서 목록 검색 조건 — {@link PageRequest} 상속.
 *
 * <p>{@code keyword} 매칭 대상: department_code / department_name / description.
 * <p>{@code parentDepartmentId} 필터: 특정 상위 부서의 직계 자식만 조회. "ROOT" 지정 시 루트만.
 */
@Getter
@Setter
public class DepartmentSearch extends PageRequest {

    /** ROOT = 루트 부서만, UUID = 해당 상위의 직계 자식, null = 전체 */
    private String parentDepartmentId;

    /** Y/N — null 이면 양쪽 */
    private String useYn;
}
