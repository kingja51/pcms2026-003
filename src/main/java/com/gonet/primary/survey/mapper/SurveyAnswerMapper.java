package com.gonet.primary.survey.mapper;

import com.gonet.primary.survey.dto.SurveyAnswer;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;

@EgovMapper
public interface SurveyAnswerMapper {

    int insert(SurveyAnswer answer);

    List<SurveyAnswer> findByResponseId(@Param("responseId") String responseId);

    /** 통계 — 객관식 옵션별 카운트. 결과 row: {optionId, cnt}. */
    List<Map<String, Object>> countByOption(@Param("questionId") String questionId);

    /** 통계 — SCALE 평균/min/max. */
    Map<String, Object> scaleStats(@Param("questionId") String questionId);

    /** 통계 — 주관식 응답 수. */
    long countTextAnswers(@Param("questionId") String questionId);

    // ------------------------------------------------------------------
    // Soft-delete retention — §0.40 ContentHistory 패턴 (조부 cutoff 기반 자식 정리)
    //
    // tb_survey_answer 자체엔 delete_yn 컬럼이 없다 (immutable audit).
    // 조부 tb_survey 의 delete_yn='Y' AND updated_at <= cutoff 의 후손 answer 들을 hard delete.
    // 경로: survey → response(자식) → answer(손자).
    // ------------------------------------------------------------------

    /** 조부 survey 의 cutoff 만료 기준 후손 answer 정리. */
    int purgeOfSoftDeletedSurvey(@Param("cutoff") LocalDateTime cutoff);

    /** dry-run — 조부 survey cutoff 기반 후손 answer 행수. */
    int countOfSoftDeletedSurvey(@Param("cutoff") LocalDateTime cutoff);

}
