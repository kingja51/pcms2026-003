package com.gonet.primary.member.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.member.mapper.MemberMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_member soft-delete retention target.
 *
 * <p>전역 공휴일 마스터
 * <p>bucket = DAYS_1, childOrder = 1.
 */
@Component
public class MemberRetentionTarget implements RetentionTarget {

    private final MemberMapper mapper;

    public MemberRetentionTarget(MemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_member"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_1; }
    @Override public int childOrder()                { return 1; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
