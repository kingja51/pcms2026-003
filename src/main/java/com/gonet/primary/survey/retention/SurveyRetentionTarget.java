package com.gonet.primary.survey.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.survey.mapper.SurveyMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_survey soft-delete retention target.
 *
 * <p>survey 마스터 — 자식 4종(question/option/response/answer) 모두 정리 후
 * <p>bucket = DAYS_90, childOrder = 30.
 */
@Component
public class SurveyRetentionTarget implements RetentionTarget {

    private final SurveyMapper mapper;

    public SurveyRetentionTarget(SurveyMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_survey"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_90; }
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
