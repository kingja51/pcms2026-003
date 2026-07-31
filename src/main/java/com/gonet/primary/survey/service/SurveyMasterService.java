package com.gonet.primary.survey.service;

import com.gonet.primary.survey.dto.SurveyMaster;
import com.gonet.primary.survey.dto.SurveyMasterSaveForm;
import com.gonet.primary.survey.dto.SurveyMasterSearch;

import java.util.List;

/**
 * 설문 마스터(그룹) 관리 — site/menu 컨텍스트 + 그룹 헤더.
 *
 * <p>설문 detail (개별 설문 인스턴스) 은 {@link SurveyService} 가 관리. 본 서비스는 master 그룹 CRUD 만 담당.
 */
public interface SurveyMasterService {

    List<SurveyMaster> search(SurveyMasterSearch search);

    int count(SurveyMasterSearch search);

    SurveyMaster get(String surveyMasterId);

    SurveyMaster findBySiteCode(String siteCode);




    String create(SurveyMasterSaveForm form);

    void update(SurveyMasterSaveForm form);

    void toggleUse(String surveyMasterId, boolean active);

    void softDelete(String surveyMasterId);
}
