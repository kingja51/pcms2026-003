package com.gonet.primary.notification.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_notification soft-delete retention target.
 *
 * <p>사용자 삭제 알림 누적 정리
 * <p>bucket = DAYS_30, childOrder = 30.
 */
@Component
public class NotificationRetentionTarget implements RetentionTarget {

    private final NotificationMapper mapper;

    public NotificationRetentionTarget(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_notification"; }
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
