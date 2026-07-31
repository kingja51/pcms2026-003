package com.gonet.primary.survey.mapper;

import com.gonet.primary.survey.dto.Survey;
import com.gonet.primary.survey.dto.SurveySearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.util.List;

@EgovMapper
public interface SurveyMapper {

    List<Survey> findList(@Param("search") SurveySearch search);

    int countList(@Param("search") SurveySearch search);

    Survey findById(@Param("surveyId") String surveyId);

    int insert(Survey survey);

    int update(Survey survey);

    int updateStatus(@Param("surveyId") String surveyId,
                      @Param("status") String status);

    int updateUseYn(@Param("surveyId") String surveyId,
                     @Param("useYn") String useYn);

    int softDelete(@Param("surveyId") String surveyId);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
