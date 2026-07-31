package com.gonet.primary.survey.mapper;

import com.gonet.primary.survey.dto.SurveyOption;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.util.List;

@EgovMapper
public interface SurveyOptionMapper {

    List<SurveyOption> findByQuestionId(@Param("questionId") String questionId);

    int insert(SurveyOption option);

    int softDeleteByQuestionId(@Param("questionId") String questionId);

    int softDeleteBySurveyId(@Param("surveyId") String surveyId);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
