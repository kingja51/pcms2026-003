package com.gonet.primary.admin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 폼의 직계 역할 체크박스 옵션 (tb_role 경량 DTO).
 */
@Getter
@Setter
public class RoleOption {

    private String roleId;
    private String roleCode;
    private String roleName;
    private String siteId;
}
