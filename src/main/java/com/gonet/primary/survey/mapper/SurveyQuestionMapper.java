package com.gonet.primary.survey.mapper;

import com.gonet.primary.survey.dto.SurveyQuestion;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.util.List;

@EgovMapper
public interface SurveyQuestionMapper {

    List<SurveyQuestion> findBySurveyId(@Param("surveyId") String surveyId);

    SurveyQuestion findById(@Param("questionId") String questionId);

    int insert(SurveyQuestion question);

    int update(SurveyQuestion question);

    /** 설문의 전체 문항 일괄 soft delete (수정 시 재구성). */
    int softDeleteBySurveyId(@Param("surveyId") String surveyId);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
