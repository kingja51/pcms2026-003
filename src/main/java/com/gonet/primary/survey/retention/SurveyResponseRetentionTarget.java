package com.gonet.primary.survey.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.survey.mapper.SurveyResponseMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_survey_response retention target — §0.40 ContentHistory 패턴.
 *
 * <p>tb_survey_response 자체에는 {@code delete_yn} 컬럼이 없다 (immutable audit). 일반
 * RetentionTarget 의 "자기 row cutoff" 와 다르게 <b>부모 tb_survey 의 cutoff 만료</b>
 * 기준으로 자식 response 들을 hard delete.
 *
 * <p>cutoff 의미:
 * <ul>
 *   <li>일반 RetentionTarget — 자기 row 의 {@code delete_yn='Y' AND updated_at <= cutoff}</li>
 *   <li>본 target — 부모 survey 의 {@code delete_yn='Y' AND updated_at <= cutoff} 인
 *       survey_id 들의 response 행</li>
 * </ul>
 *
 * <p>{@link SurveyRetentionTarget}(childOrder=30) 보다 먼저 실행되도록 childOrder=8.
 * 손자 {@link SurveyAnswerRetentionTarget}(childOrder=5) 보다는 나중 (FK 위반 회피).
 *
 * <p>bucket = DAYS_90 — 부모 survey 와 동일. 같은 cutoff 로 손자→자식→부모 일관 정리.
 */
@Component
public class SurveyResponseRetentionTarget implements RetentionTarget {

    private final SurveyResponseMapper mapper;

    public SurveyResponseRetentionTarget(SurveyResponseMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_survey_response"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_90; }
    @Override public int childOrder()                { return 8; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeOfSoftDeletedSurvey(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countOfSoftDeletedSurvey(cutoff);
    }
}
