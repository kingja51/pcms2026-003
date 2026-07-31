package com.gonet.primary.system.code.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_code_group 1건 — 공통코드 그룹 엔티티.
 */
@Getter
@Setter
public class CodeGroup extends BaseEntity implements SoftDeletable, UseFlagged {

    private String codeGroupId;
    private String groupCode;
    private String groupName;
    private String description;
    private String useYn;
    private String deleteYn;

    // JOIN 표시용
    private Integer itemCount;       // 그룹 내 활성 코드 수
}
