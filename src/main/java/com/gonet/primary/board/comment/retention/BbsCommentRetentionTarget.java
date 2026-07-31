package com.gonet.primary.board.comment.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.board.comment.mapper.BbsCommentMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_bbs_comment soft-delete retention target.
 *
 * <p>FK 종속성 — 댓글은 article 의 자식이므로 article 보다 먼저 정리된다 (childOrder=10).
 */
@Component
public class BbsCommentRetentionTarget implements RetentionTarget {

    private final BbsCommentMapper mapper;

    public BbsCommentRetentionTarget(BbsCommentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String tableName() {
        return "tb_bbs_comment";
    }

    @Override
    public RetentionBucket defaultBucket() {
        return RetentionBucket.DAYS_30;
    }

    @Override
    public int childOrder() {
        return 10;
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
