package com.gonet.primary.survey.mapper;

import com.gonet.primary.survey.dto.SurveyResponse;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.util.List;

@EgovMapper
public interface SurveyResponseMapper {

    int insert(SurveyResponse response);

    SurveyResponse findById(@Param("responseId") String responseId);

    SurveyResponse findByMember(@Param("surveyId") String surveyId,
                                  @Param("memberId") String memberId);

    long countBySurvey(@Param("surveyId") String surveyId);

    List<SurveyResponse> findBySurveyId(@Param("surveyId") String surveyId);

    // ------------------------------------------------------------------
    // Soft-delete retention — §0.40 ContentHistory 패턴 (부모 cutoff 기반 자식 정리)
    //
    // tb_survey_response 자체엔 delete_yn 컬럼이 없다 (immutable audit). 부모 tb_survey
    // 의 delete_yn='Y' AND updated_at <= cutoff 행의 response 들을 hard delete.
    // ------------------------------------------------------------------

    /** 부모 survey 의 cutoff 만료 기준 자식 response 정리. */
    int purgeOfSoftDeletedSurvey(@Param("cutoff") LocalDateTime cutoff);

    /** dry-run — 부모 survey cutoff 기반 자식 response 행수. */
    int countOfSoftDeletedSurvey(@Param("cutoff") LocalDateTime cutoff);

}
