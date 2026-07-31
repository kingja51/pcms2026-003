package com.gonet.primary.survey.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.survey.dto.QuestionType;
import com.gonet.primary.survey.dto.Survey;
import com.gonet.primary.survey.dto.SurveyAnswer;
import com.gonet.primary.survey.dto.SurveyOption;
import com.gonet.primary.survey.dto.SurveyQuestion;
import com.gonet.primary.survey.dto.SurveyResponse;
import com.gonet.primary.survey.dto.SurveyStatus;
import com.gonet.primary.survey.mapper.SurveyAnswerMapper;
import com.gonet.primary.survey.mapper.SurveyMapper;
import com.gonet.primary.survey.mapper.SurveyOptionMapper;
import com.gonet.primary.survey.mapper.SurveyQuestionMapper;
import com.gonet.primary.survey.mapper.SurveyResponseMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class SurveyResponseServiceImpl extends EgovAbstractServiceImpl implements SurveyResponseService {

    private static final Logger log = LoggerFactory.getLogger(SurveyResponseServiceImpl.class);

    private final SurveyMapper          surveyMapper;
    private final SurveyQuestionMapper  questionMapper;
    private final SurveyOptionMapper    optionMapper;
    private final SurveyResponseMapper  responseMapper;
    private final SurveyAnswerMapper    answerMapper;
    private final AuditLogger           auditLogger;

    public SurveyResponseServiceImpl(SurveyMapper surveyMapper,
                                       SurveyQuestionMapper questionMapper,
                                       SurveyOptionMapper optionMapper,
                                       SurveyResponseMapper responseMapper,
                                       SurveyAnswerMapper answerMapper,
                                       AuditLogger auditLogger) {
        this.surveyMapper   = surveyMapper;
        this.questionMapper = questionMapper;
        this.optionMapper   = optionMapper;
        this.responseMapper = responseMapper;
        this.answerMapper   = answerMapper;
        this.auditLogger    = auditLogger;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String submit(String surveyId, Map<String, Object> formAnswers,
                          CustomUserDetails me, String clientIp) {
        Objects.requireNonNull(surveyId, "surveyId");
        if (me == null || me.getUserId() == null) {
            throw new AccessDeniedException("설문 응답은 회원 로그인 후 가능합니다.");
        }
        String userType = me.getUserType() == null ? "" : me.getUserType().toUpperCase();
        if (!"MEMBER".equals(userType) && !"EMPLOYEE".equals(userType) && !"STAFF".equals(userType)) {
            throw new AccessDeniedException("회원만 설문에 응답할 수 있습니다.");
        }

        Survey sv = surveyMapper.findById(surveyId);
        if (sv == null) throw new ResourceNotFoundException("설문을 찾을 수 없습니다.");
        if (!"Y".equals(sv.getUseYn())) throw new IllegalStateException("사용중지된 설문입니다.");
        if (SurveyStatus.safeParse(sv.getStatus()) != SurveyStatus.PUBLISHED) {
            throw new IllegalStateException("응답 가능한 상태가 아닙니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sv.getStartAt()) || now.isAfter(sv.getEndAt())) {
            throw new IllegalStateException("설문 응답 기간이 아닙니다.");
        }

        boolean anonymous = "Y".equalsIgnoreCase(sv.getAnonymousYn());
        boolean oneResp   = "Y".equalsIgnoreCase(sv.getOneResponseYn());
        String memberId   = anonymous ? null : me.getUserId();

        if (oneResp && memberId != null) {
            if (responseMapper.findByMember(surveyId, memberId) != null) {
                throw new IllegalStateException("이미 응답하신 설문입니다.");
            }
        }

        // 응답 헤더 INSERT
        SurveyResponse r = new SurveyResponse();
        r.setResponseId(UuidV7Generator.generate("SRP"));
        r.setSurveyId(surveyId);
        r.setMemberId(memberId);
        r.setClientIp(clientIp);
        try {
            responseMapper.insert(r);
        } catch (DuplicateKeyException dup) {
            throw new IllegalStateException("이미 응답하신 설문입니다.", dup);
        }

        // 문항 + 선택지 로드
        List<SurveyQuestion> questions = questionMapper.findBySurveyId(surveyId);
        for (SurveyQuestion q : questions) {
            QuestionType type = QuestionType.safeParse(q.getQuestionType());
            Object raw = formAnswers == null ? null : formAnswers.get(q.getQuestionId());
            boolean missing = isMissing(raw);

            if (q.isRequired() && missing) {
                throw new IllegalArgumentException("필수 응답 누락: " + q.getQuestionText());
            }
            if (missing) continue;

            switch (type) {
                case TEXT, TEXTAREA -> {
                    SurveyAnswer a = new SurveyAnswer();
                    a.setAnswerId(UuidV7Generator.generate("SAN"));
                    a.setResponseId(r.getResponseId());
                    a.setQuestionId(q.getQuestionId());
                    a.setAnswerText(raw.toString().trim());
                    answerMapper.insert(a);
                }
                case RADIO, SELECT -> {
                    String optId = raw.toString().trim();
                    if (!validOption(q.getQuestionId(), optId)) {
                        throw new IllegalArgumentException(
                            "선택지가 올바르지 않습니다: " + q.getQuestionText());
                    }
                    SurveyAnswer a = new SurveyAnswer();
                    a.setAnswerId(UuidV7Generator.generate("SAN"));
                    a.setResponseId(r.getResponseId());
                    a.setQuestionId(q.getQuestionId());
                    a.setOptionId(optId);
                    answerMapper.insert(a);
                }
                case CHECKBOX -> {
                    List<String> ids = toStringList(raw);
                    if (ids.isEmpty() && q.isRequired()) {
                        throw new IllegalArgumentException("필수 응답 누락: " + q.getQuestionText());
                    }
                    for (String optId : ids) {
                        if (!validOption(q.getQuestionId(), optId)) {
                            throw new IllegalArgumentException(
                                "선택지가 올바르지 않습니다: " + q.getQuestionText());
                        }
                        SurveyAnswer a = new SurveyAnswer();
                        a.setAnswerId(UuidV7Generator.generate("SAN"));
                        a.setResponseId(r.getResponseId());
                        a.setQuestionId(q.getQuestionId());
                        a.setOptionId(optId);
                        answerMapper.insert(a);
                    }
                }
                case SCALE -> {
                    int n;
                    try {
                        n = Integer.parseInt(raw.toString().trim());
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException(
                            "점수 입력이 올바르지 않습니다: " + q.getQuestionText());
                    }
                    int min = q.getScaleMin() == null ? 1 : q.getScaleMin();
                    int max = q.getScaleMax() == null ? 5 : q.getScaleMax();
                    if (n < min || n > max) {
                        throw new IllegalArgumentException(
                            "점수 범위(" + min + "~" + max + ") 를 벗어났습니다: " + q.getQuestionText());
                    }
                    SurveyAnswer a = new SurveyAnswer();
                    a.setAnswerId(UuidV7Generator.generate("SAN"));
                    a.setResponseId(r.getResponseId());
                    a.setQuestionId(q.getQuestionId());
                    a.setAnswerNumber(n);
                    answerMapper.insert(a);
                }
            }
        }

        auditLogger.write(AuditEvent.of("SURVEY_RESPONSE", "tb_survey_response")
            .withTarget(r.getResponseId())
            .withAfter("{\"surveyId\":" + JsonUtils.quote(surveyId)
                + ",\"memberId\":" + JsonUtils.quote(memberId)
                + ",\"anonymous\":" + anonymous + "}")
            .withResult("SUCCESS"));

        log.info("===SURVEY_RESPONSE_OK survey={} response={} member={} anonymous={}",
            surveyId, r.getResponseId(), memberId, anonymous);
        return r.getResponseId();
    }

    @Override
    public SurveyResponse get(String responseId) {
        if (responseId == null || responseId.isBlank()) return null;
        return responseMapper.findById(responseId);
    }

    @Override
    public long count(String surveyId) {
        if (surveyId == null) return 0;
        return responseMapper.countBySurvey(surveyId);
    }

    @Override
    public List<SurveyResponse> listBySurvey(String surveyId) {
        if (surveyId == null) return List.of();
        return responseMapper.findBySurveyId(surveyId);
    }

    @Override
    public List<SurveyAnswer> answersOf(String responseId) {
        if (responseId == null) return List.of();
        return answerMapper.findByResponseId(responseId);
    }

    @Override
    public boolean hasResponded(String surveyId, String memberId) {
        if (surveyId == null || memberId == null) return false;
        return responseMapper.findByMember(surveyId, memberId) != null;
    }

    private boolean validOption(String questionId, String optionId) {
        if (optionId == null || optionId.isBlank()) return false;
        Set<String> valid = new HashSet<>();
        for (SurveyOption o : optionMapper.findByQuestionId(questionId)) {
            valid.add(o.getOptionId());
        }
        return valid.contains(optionId);
    }

    private static boolean isMissing(Object raw) {
        if (raw == null) return true;
        if (raw instanceof String s) return s.isBlank();
        if (raw instanceof List<?> l) return l.isEmpty();
        if (raw instanceof Object[] a) return a.length == 0;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> l) {
            return ((List<Object>) raw).stream()
                .map(o -> o == null ? "" : o.toString().trim())
                .filter(s -> !s.isEmpty())
                .toList();
        }
        if (raw instanceof String[] arr) {
            return java.util.Arrays.stream(arr).filter(s -> s != null && !s.isBlank())
                .map(String::trim).toList();
        }
        if (raw instanceof String s && !s.isBlank()) {
            return java.util.Arrays.stream(s.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).toList();
        }
        return List.of();
    }
}
