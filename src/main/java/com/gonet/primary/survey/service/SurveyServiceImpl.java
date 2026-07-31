package com.gonet.primary.survey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.html.HtmlSanitizer;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.survey.dto.QuestionType;
import com.gonet.primary.survey.dto.Survey;
import com.gonet.primary.survey.dto.SurveyAnswer;
import com.gonet.primary.survey.dto.SurveyOption;
import com.gonet.primary.survey.dto.SurveyQuestion;
import com.gonet.primary.survey.dto.SurveySaveForm;
import com.gonet.primary.survey.dto.SurveySearch;
import com.gonet.primary.survey.dto.SurveyStatus;
import com.gonet.primary.survey.mapper.SurveyAnswerMapper;
import com.gonet.primary.survey.mapper.SurveyMapper;
import com.gonet.primary.survey.mapper.SurveyOptionMapper;
import com.gonet.primary.survey.mapper.SurveyQuestionMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 설문 마스터 + 문항/선택지 Service.
 *
 * <p>questionsJson 파싱은 Jackson — 형식이 잘못되면 ServiceLayer 가
 * IllegalArgumentException 으로 변환.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class SurveyServiceImpl extends EgovAbstractServiceImpl implements SurveyService {

    private static final Logger log = LoggerFactory.getLogger(SurveyServiceImpl.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SurveyMapper          surveyMapper;
    private final SurveyQuestionMapper  questionMapper;
    private final SurveyOptionMapper    optionMapper;
    private final SurveyAnswerMapper    answerMapper;
    private final AuditLogger           auditLogger;
    private final HtmlSanitizer         htmlSanitizer;

    public SurveyServiceImpl(SurveyMapper surveyMapper,
                              SurveyQuestionMapper questionMapper,
                              SurveyOptionMapper optionMapper,
                              SurveyAnswerMapper answerMapper,
                              AuditLogger auditLogger,
                              HtmlSanitizer htmlSanitizer) {
        this.surveyMapper   = surveyMapper;
        this.questionMapper = questionMapper;
        this.optionMapper   = optionMapper;
        this.answerMapper   = answerMapper;
        this.auditLogger    = auditLogger;
        this.htmlSanitizer  = htmlSanitizer;
    }

    @Override
    public List<Survey> search(SurveySearch search) {
        return surveyMapper.findList(search == null ? new SurveySearch() : search);
    }

    @Override
    public int count(SurveySearch search) {
        return surveyMapper.countList(search == null ? new SurveySearch() : search);
    }

    @Override
    public Survey get(String surveyId) {
        if (surveyId == null || surveyId.isBlank()) return null;
        return surveyMapper.findById(surveyId);
    }

    @Override
    public Survey getWithDetails(String surveyId) {
        Survey sv = get(surveyId);
        if (sv == null) return null;
        List<SurveyQuestion> qs = questionMapper.findBySurveyId(surveyId);
        for (SurveyQuestion q : qs) {
            if (QuestionType.safeParse(q.getQuestionType()).hasOptions()) {
                q.setOptions(optionMapper.findByQuestionId(q.getQuestionId()));
            }
        }
        sv.setQuestions(qs);
        return sv;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(SurveySaveForm form) {
        Objects.requireNonNull(form, "form");
        validateRange(form);

        Survey sv = new Survey();
        sv.setSurveyId(UuidV7Generator.generate("SRV"));
        sv.setSurveyMasterId(form.getSurveyMasterId());
        sv.setSurveyTitle(form.getSurveyTitle().trim());
        sv.setSurveyDescription(htmlSanitizer.sanitizeContent(form.getSurveyDescription()));
        sv.setStartAt(form.getStartAt());
        sv.setEndAt(form.getEndAt());
        sv.setStatus(SurveyStatus.safeParse(form.getStatus()).name());
        sv.setAnonymousYn(SurveySaveForm.yn(form.getAnonymousYn()));
        sv.setOneResponseYn(SurveySaveForm.yn(form.getOneResponseYn()));
        sv.setUseYn(SurveySaveForm.yn(form.getUseYn()));
        sv.setDeleteYn("N");

        surveyMapper.insert(sv);

        // 문항/선택지 묶음 생성
        rewriteQuestions(sv.getSurveyId(), parseQuestions(form.getQuestionsJson()));

        auditLogger.write(AuditEvent.of("SURVEY_CREATE", "tb_survey")
            .withTarget(sv.getSurveyId())
            .withAfter("{\"title\":" + JsonUtils.quote(sv.getSurveyTitle())
                + ",\"status\":" + JsonUtils.quote(sv.getStatus()) + "}")
            .withResult("SUCCESS"));

        log.info("===SURVEY_CREATE_OK id={} title={}", sv.getSurveyId(), sv.getSurveyTitle());
        return sv.getSurveyId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(SurveySaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getSurveyId() == null || form.getSurveyId().isBlank()) {
            throw new IllegalArgumentException("surveyId required");
        }
        Survey existing = surveyMapper.findById(form.getSurveyId());
        if (existing == null) throw new IllegalArgumentException("설문을 찾을 수 없습니다.");
        validateRange(form);

        existing.setSurveyMasterId(form.getSurveyMasterId());
        existing.setSurveyTitle(form.getSurveyTitle().trim());
        existing.setSurveyDescription(htmlSanitizer.sanitizeContent(form.getSurveyDescription()));
        existing.setStartAt(form.getStartAt());
        existing.setEndAt(form.getEndAt());
        existing.setStatus(SurveyStatus.safeParse(form.getStatus()).name());
        existing.setAnonymousYn(SurveySaveForm.yn(form.getAnonymousYn()));
        existing.setOneResponseYn(SurveySaveForm.yn(form.getOneResponseYn()));
        existing.setUseYn(SurveySaveForm.yn(form.getUseYn()));

        surveyMapper.update(existing);
        rewriteQuestions(existing.getSurveyId(), parseQuestions(form.getQuestionsJson()));

        auditLogger.write(AuditEvent.of("SURVEY_UPDATE", "tb_survey")
            .withTarget(existing.getSurveyId())
            .withAfter("{\"title\":" + JsonUtils.quote(existing.getSurveyTitle())
                + ",\"status\":" + JsonUtils.quote(existing.getStatus()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void changeStatus(String surveyId, String status) {
        Survey existing = surveyMapper.findById(surveyId);
        if (existing == null) throw new IllegalArgumentException("설문을 찾을 수 없습니다.");
        SurveyStatus next = SurveyStatus.safeParse(status);
        if (next.name().equals(existing.getStatus())) return;
        surveyMapper.updateStatus(surveyId, next.name());

        auditLogger.write(AuditEvent.of("SURVEY_STATUS", "tb_survey")
            .withTarget(surveyId)
            .withBefore("{\"status\":" + JsonUtils.quote(existing.getStatus()) + "}")
            .withAfter("{\"status\":" + JsonUtils.quote(next.name()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String surveyId, boolean active) {
        Survey existing = surveyMapper.findById(surveyId);
        if (existing == null) throw new IllegalArgumentException("설문을 찾을 수 없습니다.");
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) return;
        surveyMapper.updateUseYn(surveyId, next);

        auditLogger.write(AuditEvent.of("SURVEY_USE_TOGGLE", "tb_survey")
            .withTarget(surveyId)
            .withBefore("{\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String surveyId) {
        Survey existing = surveyMapper.findById(surveyId);
        if (existing == null) throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않습니다.");
        int affected = surveyMapper.softDelete(surveyId);
        if (affected == 0) throw new IllegalStateException("삭제 처리 실패: " + surveyId);

        // 문항/선택지도 일괄 soft delete (응답은 보존 — 통계 무결성)
        optionMapper.softDeleteBySurveyId(surveyId);
        questionMapper.softDeleteBySurveyId(surveyId);

        auditLogger.write(AuditEvent.of("SURVEY_DELETE", "tb_survey")
            .withTarget(surveyId)
            .withBefore("{\"title\":" + JsonUtils.quote(existing.getSurveyTitle()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void rewriteQuestions(String surveyId, List<SurveyQuestion> questions) {
        if (surveyId == null || surveyId.isBlank()) return;
        // 기존 옵션/문항 일괄 soft delete
        optionMapper.softDeleteBySurveyId(surveyId);
        questionMapper.softDeleteBySurveyId(surveyId);
        if (questions == null || questions.isEmpty()) return;

        int order = 0;
        for (SurveyQuestion q : questions) {
            String qid = UuidV7Generator.generate("SQN");
            q.setQuestionId(qid);
            q.setSurveyId(surveyId);
            q.setSortOrder(order++);
            q.setDeleteYn("N");
            QuestionType type = QuestionType.safeParse(q.getQuestionType());
            q.setQuestionType(type.name());
            // SCALE 기본값 1~5
            if (type == QuestionType.SCALE) {
                if (q.getScaleMin() == null) q.setScaleMin(1);
                if (q.getScaleMax() == null) q.setScaleMax(5);
            }
            questionMapper.insert(q);

            if (type.hasOptions() && q.getOptions() != null) {
                int oOrder = 0;
                for (SurveyOption o : q.getOptions()) {
                    o.setOptionId(UuidV7Generator.generate("SOP"));
                    o.setQuestionId(qid);
                    o.setSortOrder(oOrder++);
                    o.setDeleteYn("N");
                    optionMapper.insert(o);
                }
            }
        }
    }

    @Override
    public Map<String, Object> statsForQuestion(String questionId) {
        Map<String, Object> out = new LinkedHashMap<>();
        SurveyQuestion q = questionMapper.findById(questionId);
        if (q == null) return out;
        QuestionType type = QuestionType.safeParse(q.getQuestionType());
        out.put("type", type.name());

        if (type.hasOptions()) {
            Map<String, Long> counts = optionCounts(questionId);
            List<SurveyOption> opts = optionMapper.findByQuestionId(questionId);
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            for (SurveyOption o : opts) {
                long cnt = counts.getOrDefault(o.getOptionId(), 0L);
                o.setCount(cnt);
                o.setPercentage(total == 0 ? 0.0 : (cnt * 100.0 / total));
            }
            out.put("options", opts);
            out.put("total", total);
        } else if (type == QuestionType.SCALE) {
            Map<String, Object> stats = answerMapper.scaleStats(questionId);
            out.put("scale", stats == null ? Map.of() : stats);
        } else {
            out.put("textCount", answerMapper.countTextAnswers(questionId));
        }
        return out;
    }

    @Override
    public Map<String, Long> optionCounts(String questionId) {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : answerMapper.countByOption(questionId)) {
            Object oid = row.get("optionId");
            Object cnt = row.get("cnt");
            if (oid != null && cnt != null) {
                result.put(oid.toString(), ((Number) cnt).longValue());
            }
        }
        return result;
    }

    @Override
    public List<SurveyOption> optionsOf(String questionId) {
        return optionMapper.findByQuestionId(questionId);
    }

    private static void validateRange(SurveySaveForm form) {
        if (form.getStartAt() == null || form.getEndAt() == null) {
            throw new IllegalArgumentException("기간을 입력해 주세요.");
        }
        if (!form.getStartAt().isBefore(form.getEndAt())) {
            throw new IllegalArgumentException("시작이 종료보다 이전이어야 합니다.");
        }
    }

    /** questionsJson 파싱 — Jackson. 빈 입력은 빈 리스트. */
    private List<SurveyQuestion> parseQuestions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> arr = JSON.readValue(json,
                new TypeReference<List<Map<String, Object>>>() {});
            List<SurveyQuestion> out = new ArrayList<>(arr.size());
            for (Map<String, Object> row : arr) {
                SurveyQuestion q = new SurveyQuestion();
                q.setQuestionText(strOrThrow(row.get("text"), "문항 내용은 필수"));
                q.setQuestionType(strOr(row.get("type"), "TEXT"));
                q.setRequiredYn("Y".equalsIgnoreCase(strOr(row.get("required"), "Y")) ? "Y" : "N");
                Object min = row.get("scaleMin");
                Object max = row.get("scaleMax");
                if (min instanceof Number n) q.setScaleMin(n.intValue());
                if (max instanceof Number n) q.setScaleMax(n.intValue());

                Object opts = row.get("options");
                if (opts instanceof List<?> ol) {
                    List<SurveyOption> options = new ArrayList<>();
                    for (Object o : ol) {
                        if (o instanceof Map<?, ?> om) {
                            SurveyOption so = new SurveyOption();
                            Object ot = om.get("text");
                            if (ot == null || ot.toString().isBlank()) continue;
                            so.setOptionText(ot.toString().trim());
                            Object ov = om.get("value");
                            if (ov != null) so.setOptionValue(ov.toString().trim());
                            options.add(so);
                        }
                    }
                    q.setOptions(options);
                }
                out.add(q);
            }
            return out;
        } catch (Exception ex) {
            throw new IllegalArgumentException("문항 데이터 형식이 올바르지 않습니다: " + ex.getMessage());
        }
    }

    private static String strOr(Object o, String def) {
        return (o == null || o.toString().isBlank()) ? def : o.toString().trim();
    }

    private static String strOrThrow(Object o, String msg) {
        String s = (o == null) ? null : o.toString().trim();
        if (s == null || s.isBlank()) throw new IllegalArgumentException(msg);
        return s;
    }

    /** 응답이 없는 도메인용 단순 fallback — Service 외부에서는 사용하지 않음. */
    @SuppressWarnings("unused")
    private static SurveyAnswer dummy() { return new SurveyAnswer(); }
}
