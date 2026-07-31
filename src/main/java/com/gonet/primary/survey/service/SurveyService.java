package com.gonet.primary.survey.service;

import com.gonet.primary.survey.dto.Survey;
import com.gonet.primary.survey.dto.SurveyOption;
import com.gonet.primary.survey.dto.SurveyQuestion;
import com.gonet.primary.survey.dto.SurveySaveForm;
import com.gonet.primary.survey.dto.SurveySearch;

import java.util.List;
import java.util.Map;

/**
 * 설문 마스터 서비스 — CRUD + 문항/선택지 관리.
 */
public interface SurveyService {

    List<Survey> search(SurveySearch search);

    int count(SurveySearch search);

    Survey get(String surveyId);

    /** 마스터 + 문항 + 선택지 까지 한 번에 채워서 반환. */
    Survey getWithDetails(String surveyId);

    String create(SurveySaveForm form);

    void update(SurveySaveForm form);

    void changeStatus(String surveyId, String status);

    void toggleUse(String surveyId, boolean active);

    void softDelete(String surveyId);

    /** 문항/선택지를 한 묶음으로 다시 작성 (기존 일괄 soft delete 후 재 INSERT). */
    void rewriteQuestions(String surveyId, List<SurveyQuestion> questions);

    /** 통계 — 문항별 옵션 카운트 / SCALE / 주관식 응답수. */
    Map<String, Object> statsForQuestion(String questionId);

    /** 객관식 옵션 카운트 (optionId → cnt). */
    Map<String, Long> optionCounts(String questionId);

    /** 옵션 목록 (사용자 설문 폼/통계용). */
    List<SurveyOption> optionsOf(String questionId);
}
