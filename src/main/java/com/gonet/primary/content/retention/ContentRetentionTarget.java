package com.gonet.primary.content.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.content.mapper.ContentMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_content soft-delete retention target.
 *
 * <p>FK 종속성 — content 는 content_history 의 부모. 자식 history 가 먼저 정리되어야 FK 위반
 * 회피. 같은 cutoff 안에서 {@link ContentHistoryRetentionTarget}(childOrder=10) 가 먼저
 * 실행되고 본 target(childOrder=30) 이 그 다음 실행되도록 ordering.
 *
 * <p>bucket = DAYS_90 — 콘텐츠는 운영 의사결정에 시간 여유가 필요한 도메인 (잘못 삭제했을 때
 * 복구할 시간 확보). 일반 게시글(DAYS_30) 보다 길게.
 */
@Component
public class ContentRetentionTarget implements RetentionTarget {

    private final ContentMapper mapper;

    public ContentRetentionTarget(ContentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String tableName() {
        return "tb_content";
    }

    @Override
    public RetentionBucket defaultBucket() {
        return RetentionBucket.DAYS_90;
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
