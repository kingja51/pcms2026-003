package com.gonet.primary.system.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 부서 등록/수정 폼 (tb_department).
 *
 * <p>트리 구조 처리:
 * <ul>
 *   <li>{@code parentDepartmentId} 가 NULL/빈 문자열 → 루트 부서</li>
 *   <li>{@code depth} 는 Service 에서 parent.depth+1 로 자동 계산 — 폼 미입력</li>
 *   <li>{@code managerEmployeeId} 는 별도 엔드포인트에서 관리 — 본 폼 미노출</li>
 *   <li>자기 자신을 parent 로 지정 시 Service 에서 거부 (순환 방지)</li>
 * </ul>
 */
@Getter
@Setter
public class DepartmentSaveForm {

    private String departmentId;    // 수정 시 hidden

    @Size(max = 40)
    private String parentDepartmentId;   // 빈값 또는 null = 루트

    @NotBlank
    @Size(min = 2, max = 30)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]+$",
             message = "부서 코드는 영대문자/숫자/언더스코어만 (첫 글자 영대문자, 2자 이상)")
    private String departmentCode;

    @NotBlank
    @Size(max = 100)
    private String departmentName;

    /** 같은 부모 내 정렬 순서. 0 이상 정수. */
    private Integer sortOrder = 0;

    @Size(max = 500)
    private String description;

    @Pattern(regexp = "^[YN]$")
    private String useYn = "Y";
}
