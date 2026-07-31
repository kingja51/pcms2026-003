package com.gonet.primary.system.department.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_department 1건 — 부서 엔티티.
 *
 * <p>현 스프린트는 읽기 전용 (직원 도메인 FK 소스). 부서 자체 CRUD 는 별도 스프린트.
 */
@Getter
@Setter
public class Department extends BaseEntity implements SoftDeletable, UseFlagged {

    private String  departmentId;
    private String  parentDepartmentId;
    private String  departmentCode;
    private String  departmentName;
    private Integer depth;
    private Integer sortOrder;
    private String  managerEmployeeId;
    private String  description;
    private String  useYn;
    private String  deleteYn;
}
