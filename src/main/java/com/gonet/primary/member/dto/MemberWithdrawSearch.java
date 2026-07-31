package com.gonet.primary.member.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MemberWithdrawSearch extends PageRequest {
    private String    siteId;
    private String    withdrawReason;   // USER_REQUEST / ADMIN_FORCE / DORMANT_EXPIRED
    private LocalDate withdrawFrom;
    private LocalDate withdrawTo;
    /** 만료 임박 (retention_expire_at 가 from~to 사이) */
    private LocalDate expireFrom;
    private LocalDate expireTo;
}
