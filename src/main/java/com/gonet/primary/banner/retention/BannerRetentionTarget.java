package com.gonet.primary.banner.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.banner.mapper.BannerMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_banner soft-delete retention target.
 *
 * <p>FK 종속성 — 자식 도메인 없음. 마스터 단독으로 hard delete 안전.
 */
@Component
public class BannerRetentionTarget implements RetentionTarget {

    private final BannerMapper mapper;

    public BannerRetentionTarget(BannerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String tableName() {
        return "tb_banner";
    }

    @Override
    public RetentionBucket defaultBucket() {
        return RetentionBucket.DAYS_30;
    }

    @Override
    public int childOrder() {
        return 30;
    }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
