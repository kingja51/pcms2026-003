package com.gonet.primary.admin.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * tb_admin_group 1건 — 관리자 그룹(정책 단위).
 *
 * <p>그룹 단위로 IP 화이트리스트 / 2FA 강제 / 로그인 허용 시간 정책을 설정하여
 * 소속 관리자에게 일괄 적용. 개인 관리자 레코드의 동일 필드가 비어 있으면 그룹 값을 상속.
 */
@Getter
@Setter
public class AdminGroup extends BaseEntity implements SoftDeletable, UseFlagged {

    private String    adminGroupId;
    private String    groupCode;
    private String    groupName;
    private String    description;
    private String    defaultRoleId;
    private String    ipWhitelist;         // CIDR CSV
    private LocalTime allowedTimeFrom;
    private LocalTime allowedTimeTo;
    private String    twoFactorRequired;   // Y/N
    private String    passwordPolicyId;
    private String    useYn;
    private String    deleteYn;

    // JOIN 표시용
    private Integer   memberCount;         // 그룹에 속한 관리자 수
}
