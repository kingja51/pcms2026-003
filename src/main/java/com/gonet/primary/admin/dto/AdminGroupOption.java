package com.gonet.primary.admin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 폼의 그룹 드롭다운 옵션 (tb_admin_group 경량 DTO).
 */
@Getter
@Setter
public class AdminGroupOption {

    private String adminGroupId;
    private String groupCode;
    private String groupName;
}
