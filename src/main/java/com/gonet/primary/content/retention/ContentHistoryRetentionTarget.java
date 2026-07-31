package com.gonet.primary.content.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.content.mapper.ContentMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_content_history 자식 정리 target — 부모 content 가 hard delete 되기 전에 먼저 실행.
 *
 * <p>tb_content_history 는 immutable audit trail 이라 자체 delete_yn 컬럼이 없다.
 * 본 target 은 일반 RetentionTarget 의미 (자기 row 의 cutoff 만료) 와 다르게,
 * <b>부모 content 의 cutoff 만료</b> 를 기준으로 자식 history 를 hard delete.
 *
 * <p>cutoff 의미:
 * <ul>
 *   <li>일반 RetentionTarget — 자기 row 의 {@code delete_yn='Y' AND updated_at &lt;= cutoff}</li>
 *   <li>본 target — 부모 content 의 {@code delete_yn='Y' AND updated_at &lt;= cutoff} 인
 *       content_id 들의 history 행</li>
 * </ul>
 *
 * <p>{@link ContentRetentionTarget}(childOrder=30) 보다 먼저 실행되도록 childOrder=10
 * 부여 — FK 제약 (fk_content_hst) 위반 회피.
 */
@Component
public class ContentHistoryRetentionTarget implements RetentionTarget {

    private final ContentMapper mapper;

    public ContentHistoryRetentionTarget(ContentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String tableName() {
        return "tb_content_history";
    }

    @Override
    public RetentionBucket defaultBucket() {
        // 부모 content 의 bucket 과 일치해야 의미 정합. 같은 cutoff 로 자식 → 부모 순서 정리.
        return RetentionBucket.DAYS_90;
    }

    @Override
    public int childOrder() {
        return 10;
    }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeHistoryOfSoftDeletedContent(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countHistoryOfSoftDeletedContent(cutoff);
    }
}
