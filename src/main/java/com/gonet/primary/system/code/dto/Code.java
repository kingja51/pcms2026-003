package com.gonet.primary.system.code.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_code 1건 — 공통코드 엔티티.
 */
@Getter
@Setter
public class Code extends BaseEntity implements SoftDeletable, UseFlagged {

    private String  codeId;
    private String  codeGroupId;
    private String  code;
    private String  codeName;
    private String  codeValue;
    private Integer sortOrder;
    private String  useYn;
    private String  deleteYn;

    // JOIN 표시용
    private String  groupCode;
    private String  groupName;
}
