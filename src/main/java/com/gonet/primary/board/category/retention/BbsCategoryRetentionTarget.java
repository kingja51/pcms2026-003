package com.gonet.primary.board.category.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.board.category.mapper.BbsCategoryMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_bbs_category soft-delete retention target.
 *
 * <p>FK 종속성 — bbs_article.category_id 가 참조하는 부모. article(20) 의 부모이지만
 * comment(10) 손자보다는 늦게 정리되어야 정합 — childOrder=15 (file 15 와 같은 층위).
 * <p>bucket = DAYS_30 — 일반 게시판 정책과 일관.
 */
@Component
public class BbsCategoryRetentionTarget implements RetentionTarget {

    private final BbsCategoryMapper mapper;

    public BbsCategoryRetentionTarget(BbsCategoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_bbs_category"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_30; }
    @Override public int childOrder()                { return 15; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
