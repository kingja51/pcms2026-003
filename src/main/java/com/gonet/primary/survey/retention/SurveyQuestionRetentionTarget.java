package com.gonet.primary.survey.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.survey.mapper.SurveyQuestionMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_survey_question soft-delete retention target.
 *
 * <p>survey 자식, option 부모
 * <p>bucket = DAYS_90, childOrder = 10.
 */
@Component
public class SurveyQuestionRetentionTarget implements RetentionTarget {

    private final SurveyQuestionMapper mapper;

    public SurveyQuestionRetentionTarget(SurveyQuestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_survey_question"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_90; }
    @Override public int childOrder()                { return 10; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
