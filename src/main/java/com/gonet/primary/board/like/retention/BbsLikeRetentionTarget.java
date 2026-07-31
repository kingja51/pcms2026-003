package com.gonet.primary.board.like.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.board.like.mapper.BbsLikeMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_bbs_like soft-delete retention target.
 *
 * <p>article(20)/comment(10) 자식. UNIQUE 제약 — article soft delete 시 함께 정리
 * <p>bucket = DAYS_30, childOrder = 5.
 */
@Component
public class BbsLikeRetentionTarget implements RetentionTarget {

    private final BbsLikeMapper mapper;

    public BbsLikeRetentionTarget(BbsLikeMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_bbs_like"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_30; }
    @Override public int childOrder()                { return 5; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
