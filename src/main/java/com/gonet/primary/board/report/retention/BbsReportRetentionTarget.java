package com.gonet.primary.board.report.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.board.report.mapper.BbsReportMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_bbs_report soft-delete retention target.
 *
 * <p>신고 audit — 길게 보존 위해 DAYS_90. article 자식 (childOrder 5)
 * <p>bucket = DAYS_90, childOrder = 5.
 */
@Component
public class BbsReportRetentionTarget implements RetentionTarget {

    private final BbsReportMapper mapper;

    public BbsReportRetentionTarget(BbsReportMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_bbs_report"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_90; }
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
