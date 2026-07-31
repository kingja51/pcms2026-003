package com.gonet.primary.board.article.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.board.article.mapper.BbsArticleMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_bbs_article soft-delete retention target.
 *
 * <p>FK 종속성 — article 은 comment 의 부모이므로 comment 정리 후 실행 (childOrder=20).
 */
@Component
public class BbsArticleRetentionTarget implements RetentionTarget {

    private final BbsArticleMapper mapper;

    public BbsArticleRetentionTarget(BbsArticleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String tableName() {
        return "tb_bbs_article";
    }

    @Override
    public RetentionBucket defaultBucket() {
        return RetentionBucket.DAYS_30;
    }

    @Override
    public int childOrder() {
        return 20;
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
