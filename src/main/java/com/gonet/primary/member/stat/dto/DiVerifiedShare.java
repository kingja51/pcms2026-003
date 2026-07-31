package com.gonet.primary.member.stat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 실명인증(di_hash NOT NULL) 보유 회원 vs 미인증 회원수.
 *
 * <p>tb_member 활성 회원에 한해 di_hash 가 비어 있지 않은 비율로 집계.
 */
@Getter
@Setter
public class DiVerifiedShare {

    private long verified;
    private long unverified;

    public long getTotal() {
        return verified + unverified;
    }
}
