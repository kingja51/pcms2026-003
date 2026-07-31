package com.gonet.primary.schedule.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.schedule.mapper.ScheduleMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_schedule soft-delete retention target.
 *
 * <p>사이트별 일정
 * <p>bucket = DAYS_30, childOrder = 30.
 */
@Component
public class ScheduleRetentionTarget implements RetentionTarget {

    private final ScheduleMapper mapper;

    public ScheduleRetentionTarget(ScheduleMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_schedule"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_30; }
    @Override public int childOrder()                { return 30; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
