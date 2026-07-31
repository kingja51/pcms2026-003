package com.gonet.primary.member.lifecycle;

import com.gonet.common.lifecycle.WithdrawPurgeTarget;
import com.gonet.primary.member.mapper.MemberMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_member_withdraw 만료 영구 삭제 target.
 *
 * <p>회원 보존 정책 — 탈퇴 후 1년 (PIPA §29 기본) 또는 등록 시점에 부여한
 * retention_expire_at. 만료된 행 hard delete.
 */
@Component
public class MemberWithdrawPurgeTarget implements WithdrawPurgeTarget {

    private final MemberMapper mapper;

    public MemberWithdrawPurgeTarget(MemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String tableName() {
        return "tb_member_withdraw";
    }

    @Override
    public int purgeExpired(LocalDateTime now) {
        return mapper.purgeExpiredWithdraw(now);
    }

    @Override
    public int countExpired(LocalDateTime now) {
        return mapper.countExpiredWithdraw(now);
    }
}
