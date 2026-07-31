package com.gonet.primary.survey.service;

import com.gonet.primary.survey.dto.SurveyAnswer;
import com.gonet.primary.survey.dto.SurveyResponse;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 설문 응답 Service — 회원이 설문에 답을 제출하는 엔드포인트와 결과 조회.
 *
 * <p>제출 시 검증:
 * <ul>
 *   <li>설문 PUBLISHED + 기간 내</li>
 *   <li>회원 인증 필수 (anonymousYn='Y' 인 경우에도 회원만 응답 — 단 member_id 는 NULL 저장)</li>
 *   <li>oneResponseYn='Y' 인 경우 해당 회원이 이미 응답했으면 거부</li>
 *   <li>required_yn='Y' 문항은 응답 필수</li>
 * </ul>
 */
public interface SurveyResponseService {

    /**
     * 응답 제출.
     *
     * @param surveyId 설문 ID
     * @param formAnswers question_id → 응답 값 (text 면 String, 객관식 단일이면 optionId String,
     *                    객관식 다중이면 String[] 또는 List<String>, SCALE 이면 String/Integer)
     * @param me 회원 (인증 필수)
     * @param clientIp 응답자 IP
     * @return 발급된 response_id
     */
    String submit(String surveyId, Map<String, Object> formAnswers,
                   CustomUserDetails me, String clientIp);

    SurveyResponse get(String responseId);

    long count(String surveyId);

    List<SurveyResponse> listBySurvey(String surveyId);

    List<SurveyAnswer> answersOf(String responseId);

    /** 회원이 이미 응답했는지 — null/false 면 응답 가능. */
    boolean hasResponded(String surveyId, String memberId);
}
