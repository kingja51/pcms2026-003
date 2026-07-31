package com.gonet.primary.member.stat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * OAuth2 provider 별 매핑 수 (tb_member_oauth, soft-deleted 제외).
 */
@Getter
@Setter
public class ProviderCount {

    private String provider;
    private long   total;
}
