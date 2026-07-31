package com.gonet.primary.member.stat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 탈퇴 사유 카테고리(withdraw_status) 별 카운트.
 *
 * <p>카테고리: {@code USER_REQUEST} / {@code ADMIN_FORCE} / {@code DORMANT_EXPIRED}.
 */
@Getter
@Setter
public class WithdrawStatusCount {

    private String withdrawStatus;
    private long   total;
}
