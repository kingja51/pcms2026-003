package com.gonet.primary.member.stat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 사이트별 활성 회원수 (tb_member, soft-deleted 제외).
 */
@Getter
@Setter
public class SiteMemberCount {

    private String siteId;
    private String siteCode;
    private String siteName;
    private long   total;
}
