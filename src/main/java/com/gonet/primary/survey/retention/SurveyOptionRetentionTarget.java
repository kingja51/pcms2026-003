package com.gonet.primary.survey.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.survey.mapper.SurveyOptionMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_survey_option soft-delete retention target.
 *
 * <p>question 자식
 * <p>bucket = DAYS_90, childOrder = 5.
 */
@Component
public class SurveyOptionRetentionTarget implements RetentionTarget {

    private final SurveyOptionMapper mapper;

    public SurveyOptionRetentionTarget(SurveyOptionMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_survey_option"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_90; }
    @Override public int childOrder()                { return 5; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
