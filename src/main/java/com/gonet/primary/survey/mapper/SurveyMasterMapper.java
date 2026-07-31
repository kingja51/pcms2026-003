package com.gonet.primary.survey.mapper;

import com.gonet.primary.survey.dto.SurveyMaster;
import com.gonet.primary.survey.dto.SurveyMasterSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@EgovMapper
public interface SurveyMasterMapper {

    List<SurveyMaster> findList(@Param("search") SurveyMasterSearch search);

    int countList(@Param("search") SurveyMasterSearch search);

    SurveyMaster findById(@Param("surveyMasterId") String surveyMasterId);

    /** 사이트의 첫 활성 마스터 1개 — 사용자 측 진입점에서 컨텍스트로 사용. */
    SurveyMaster findBySiteCode(@Param("siteCode") String siteCode);

    int insert(SurveyMaster master);

    int update(SurveyMaster master);

    /** master_title / master_content 만 갱신 — detail 의 survey_title 변경 시 sync 용. */
    int updateTitle(@Param("surveyMasterId") String surveyMasterId,
                    @Param("masterTitle") String masterTitle,
                    @Param("updatedBy") String updatedBy,
                    @Param("updatedIp") String updatedIp);

    int updateUseYn(@Param("surveyMasterId") String surveyMasterId,
                    @Param("useYn") String useYn);

    int softDelete(@Param("surveyMasterId") String surveyMasterId);
}
