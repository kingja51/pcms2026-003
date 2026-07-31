package com.gonet.primary.holiday.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.holiday.mapper.HolidayMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_holiday soft-delete retention target.
 *
 * <p>전역 공휴일 마스터
 * <p>bucket = DAYS_30, childOrder = 30.
 */
@Component
public class HolidayRetentionTarget implements RetentionTarget {

    private final HolidayMapper mapper;

    public HolidayRetentionTarget(HolidayMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_holiday"; }
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
