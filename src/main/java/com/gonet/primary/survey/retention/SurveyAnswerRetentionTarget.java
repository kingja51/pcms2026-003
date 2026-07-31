package com.gonet.primary.survey.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.survey.mapper.SurveyAnswerMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_survey_answer retention target — §0.40 ContentHistory 패턴.
 *
 * <p>tb_survey_answer 자체에는 {@code delete_yn} 컬럼이 없다 (immutable audit).
 * <b>조부 tb_survey 의 cutoff 만료</b> 기준으로 후손 answer 행 hard delete.
 *
 * <p>경로 — survey → response(자식) → answer(손자). 본 target 은 손자라 가장 먼저
 * 정리되어야 FK 제약(fk_answer_response) 위반 회피.
 *
 * <p>childOrder = 5 — 손자 (가장 깊은 자식). 자식 response(8), 부모 survey(30) 보다 먼저.
 *
 * <p>bucket = DAYS_90 — 조부 survey 와 동일. 같은 cutoff 로 후손→자식→조부 일관 정리.
 */
@Component
public class SurveyAnswerRetentionTarget implements RetentionTarget {

    private final SurveyAnswerMapper mapper;

    public SurveyAnswerRetentionTarget(SurveyAnswerMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_survey_answer"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_90; }
    @Override public int childOrder()                { return 5; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeOfSoftDeletedSurvey(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countOfSoftDeletedSurvey(cutoff);
    }
}
