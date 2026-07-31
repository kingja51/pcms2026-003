package com.gonet.primary.survey.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.survey.dto.Survey;
import com.gonet.primary.survey.dto.SurveyAnswer;
import com.gonet.primary.survey.dto.SurveyOption;
import com.gonet.primary.survey.dto.SurveyQuestion;
import com.gonet.primary.survey.dto.SurveyResponse;
import com.gonet.primary.survey.dto.SurveySaveForm;
import com.gonet.primary.survey.dto.SurveySearch;
import com.gonet.primary.survey.dto.SurveyStatus;
import com.gonet.primary.survey.dto.SurveyMaster;
import com.gonet.primary.survey.dto.SurveyMasterSearch;
import com.gonet.primary.survey.service.SurveyMasterService;
import com.gonet.primary.survey.service.SurveyResponseService;
import com.gonet.primary.survey.service.SurveyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/system/survey")
public class SurveyMngController {

    private static final Logger log = LoggerFactory.getLogger(SurveyMngController.class);

    // script 블록 삽입 안전(HTML 이스케이프) — 통계 페이로드 방어심층.
    private static final ObjectMapper JSON = com.gonet.common.util.HtmlSafeJson.mapper();

    private final SurveyService         service;
    private final SurveyResponseService responseService;
    private final SurveyMasterService   masterService;
    private final AuditLogger           auditLogger;
    private final ExcelResponseWriter   excelWriter;

    public SurveyMngController(SurveyService service,
                                 SurveyResponseService responseService,
                                 SurveyMasterService masterService,
                                 AuditLogger auditLogger,
                                 ExcelResponseWriter excelWriter) {
        this.service         = service;
        this.responseService = responseService;
        this.masterService   = masterService;
        this.auditLogger     = auditLogger;
        this.excelWriter     = excelWriter;
    }

    @GetMapping
    public String list(@ModelAttribute("search") SurveySearch search, Model model) {
        List<Survey> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("masters", listMasters());
        model.addAttribute("statuses", SurveyStatus.values());
        return "admin/system/survey/list";
    }

    @GetMapping("/{surveyId}")
    public String detail(@PathVariable String surveyId, Model model, RedirectAttributes ra) {
        Survey sv = service.getWithDetails(surveyId);
        if (sv == null) {
            ra.addFlashAttribute("error", "설문을 찾을 수 없습니다.");
            return "redirect:/admin/system/survey";
        }
        // 통계 — 문항별
        Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
        if (sv.getQuestions() != null) {
            for (SurveyQuestion q : sv.getQuestions()) {
                stats.put(q.getQuestionId(), service.statsForQuestion(q.getQuestionId()));
            }
        }
        model.addAttribute("survey", sv);
        model.addAttribute("stats", stats);
        model.addAttribute("responseCount", responseService.count(surveyId));
        return "admin/system/survey/detail";
    }

    @GetMapping("/{surveyId}/responses")
    public String responses(@PathVariable String surveyId, Model model, RedirectAttributes ra) {
        Survey sv = service.get(surveyId);
        if (sv == null) {
            ra.addFlashAttribute("error", "설문을 찾을 수 없습니다.");
            return "redirect:/admin/system/survey";
        }
        List<SurveyResponse> rs = responseService.listBySurvey(surveyId);
        model.addAttribute("survey", sv);
        model.addAttribute("responses", rs);
        return "admin/system/survey/responses";
    }

    /**
     * 응답 통계 — 문항별 차트 + 요약 통계.
     *
     * <p>차트 종류:
     * <ul>
     *   <li>RADIO/SELECT — 도넛 차트 (옵션별 선택수 + 비율)</li>
     *   <li>CHECKBOX — 가로 막대 차트 (다중 선택 가능, 합계가 응답수와 다를 수 있음)</li>
     *   <li>SCALE — 평균/min/max + 응답수 카드 표시</li>
     *   <li>TEXT/TEXTAREA — 응답수만 (개별 응답은 응답 목록에서 확인)</li>
     * </ul>
     */
    @GetMapping("/{surveyId}/stat")
    public String stat(@PathVariable String surveyId, Model model, RedirectAttributes ra) {
        Survey sv = service.getWithDetails(surveyId);
        if (sv == null) {
            ra.addFlashAttribute("error", "설문을 찾을 수 없습니다.");
            return "redirect:/admin/system/survey";
        }
        // 문항별 통계 계산
        Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
        if (sv.getQuestions() != null) {
            for (SurveyQuestion q : sv.getQuestions()) {
                stats.put(q.getQuestionId(), service.statsForQuestion(q.getQuestionId()));
            }
        }
        // Chart.js 가 사용할 직렬화된 통계 페이로드 — 클라이언트 JS 가 JSON.parse
        String statsJson;
        try {
            statsJson = JSON.writeValueAsString(buildChartPayload(sv, stats));
        } catch (Exception ex) {
            log.warn("SURVEY_STAT json error", ex);
            statsJson = "[]";
        }
        model.addAttribute("survey", sv);
        model.addAttribute("stats", stats);
        model.addAttribute("statsJson", statsJson);
        model.addAttribute("responseCount", responseService.count(surveyId));
        return "admin/system/survey/stat";
    }

    /** Chart.js 친화적 페이로드 — 문항 목록 + 옵션/SCALE/text 카운트 통합. */
    private static List<Map<String, Object>> buildChartPayload(Survey sv,
                                                                  Map<String, Map<String, Object>> stats) {
        List<Map<String, Object>> arr = new ArrayList<>();
        if (sv.getQuestions() == null) return arr;

        for (SurveyQuestion q : sv.getQuestions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", q.getQuestionId());
            item.put("questionText", q.getQuestionText());
            item.put("questionType", q.getQuestionType());
            item.put("requiredYn", q.getRequiredYn());
            Map<String, Object> s = stats.get(q.getQuestionId());

            // 객관식 (RADIO/CHECKBOX/SELECT)
            if (s != null && s.get("options") instanceof List<?> opts) {
                List<Map<String, Object>> labels = new ArrayList<>();
                for (Object o : opts) {
                    if (o instanceof SurveyOption opt) {
                        Map<String, Object> ol = new LinkedHashMap<>();
                        ol.put("text", opt.getOptionText());
                        ol.put("count", opt.getCount());
                        ol.put("percentage", opt.getPercentage());
                        labels.add(ol);
                    }
                }
                item.put("options", labels);
                item.put("total", s.get("total"));
            }
            // SCALE
            if (s != null && s.get("scale") instanceof Map<?, ?> scale && !scale.isEmpty()) {
                item.put("scale", scale);
            }
            // 주관식
            if (s != null && s.get("textCount") != null) {
                item.put("textCount", s.get("textCount"));
            }
            arr.add(item);
        }
        return arr;
    }

    /**
     * 응답 CSV 다운로드 — Excel 호환 (UTF-8 BOM + RFC 4180).
     *
     * <p>한 응답이 한 행. 컬럼 = 고정 4컬럼(응답ID/제출시각/응답자/IP) + 문항별 N컬럼.
     * 객관식 다중 응답은 같은 셀에 {@code |} 구분자로 결합. 주관식은 답변 텍스트 그대로,
     * SCALE 은 숫자값. 익명 설문은 응답자 컬럼이 "(익명)" 으로 마스킹.
     *
     * <p>다운로드 사유 필수(10자 이상) — 일반 EXCEL_DOWNLOAD 와 동일 패턴으로 감사 적재.
     */
    @PostMapping("/{surveyId}/responses/csv")
    public String responsesCsv(@PathVariable String surveyId,
                                 @Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                                 BindingResult br,
                                 HttpServletRequest req,
                                 HttpServletResponse res,
                                 RedirectAttributes ra) throws IOException {
        String redirect = "/admin/system/survey/" + surveyId + "/responses";
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:" + redirect;
        }
        Survey sv = service.getWithDetails(surveyId);
        if (sv == null) {
            ra.addFlashAttribute("error", "설문을 찾을 수 없습니다.");
            return "redirect:/admin/system/survey";
        }

        List<SurveyResponse> responses = responseService.listBySurvey(surveyId);
        List<SurveyQuestion> questions = sv.getQuestions() == null ? List.of() : sv.getQuestions();
        boolean anonymous = "Y".equalsIgnoreCase(sv.getAnonymousYn());

        // 파일명 — 영문/타임스탬프로 안전화 + 한글 원문은 RFC 5987 filename* 로 별도 제공
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeStem = "survey_responses_" + stamp + ".csv";
        String niceStem = (sv.getSurveyTitle() == null ? "survey_responses" : sv.getSurveyTitle())
            + "_" + stamp + ".csv";

        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("text/csv; charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "private, no-store");
        res.setHeader("Content-Disposition",
            "attachment; filename=\"" + safeStem + "\"; filename*=UTF-8''"
                + URLEncoder.encode(niceStem, StandardCharsets.UTF_8).replace("+", "%20"));

        try (OutputStream out = res.getOutputStream()) {
            // UTF-8 BOM — Excel 한글 깨짐 방지
            out.write(0xEF); out.write(0xBB); out.write(0xBF);

            StringBuilder sb = new StringBuilder(8192);
            // ── 헤더 행
            sb.append(csv("응답ID")).append(',')
              .append(csv("제출시각")).append(',')
              .append(csv("응답자")).append(',')
              .append(csv("IP"));
            int qIdx = 1;
            for (SurveyQuestion q : questions) {
                sb.append(',').append(csv("Q" + qIdx + ". " + nvl(q.getQuestionText())));
                qIdx++;
            }
            sb.append("\r\n");

            // ── 데이터 행
            for (SurveyResponse r : responses) {
                List<SurveyAnswer> answers = responseService.answersOf(r.getResponseId());
                Map<String, List<SurveyAnswer>> byQuestion = new HashMap<>();
                if (answers != null) {
                    for (SurveyAnswer a : answers) {
                        byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
                    }
                }

                sb.append(csv(nvl(r.getResponseId()))).append(',')
                  .append(csv(str(r.getSubmittedAt()))).append(',');
                if (anonymous || r.getMemberId() == null) {
                    sb.append(csv("(익명)"));
                } else {
                    String label = nvl(r.getMemberLoginId() != null ? r.getMemberLoginId() : r.getMemberId());
                    if (r.getMemberName() != null && !r.getMemberName().isBlank()) {
                        label = label + " (" + r.getMemberName() + ")";
                    }
                    sb.append(csv(label));
                }
                sb.append(',').append(csv(nvl(r.getClientIp())));

                for (SurveyQuestion q : questions) {
                    sb.append(',').append(csv(formatAnswer(q, byQuestion.get(q.getQuestionId()))));
                }
                sb.append("\r\n");

                // 누적 버퍼가 너무 커지지 않도록 32KB 단위로 flush
                if (sb.length() > 32_768) {
                    out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    sb.setLength(0);
                }
            }
            if (sb.length() > 0) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
        }

        // 감사 — EXCEL_DOWNLOAD 패턴 그대로 (entity = tb_survey_response 로 구분)
        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_survey_response")
            .withTarget(surveyId)
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"format\":\"csv\",\"surveyId\":" + JsonUtils.quote(surveyId)
                + ",\"count\":" + responses.size()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));

        log.info("===SURVEY_RESPONSE_CSV ok surveyId={} count={}", surveyId, responses.size());
        return null;
    }

    /**
     * 응답 엑셀(.xlsx) 다운로드 — 결과 내역 통합 시트.
     *
     * <p>구성: <strong>요약 시트(Survey)</strong> 1행 메타 + <strong>데이터 행</strong> N개.
     * CSV 와 동일하게 한 응답이 한 행, 컬럼 = 고정 4(응답ID/제출시각/응답자/IP) + 문항별 N.
     * 객관식 다중은 {@code |} 구분, SCALE 은 숫자, 익명 설문은 응답자 마스킹.
     *
     * <p>다운로드 사유 필수(10자 이상) — EXCEL_DOWNLOAD audit + entity={@code tb_survey_response}.
     */
    @PostMapping("/{surveyId}/responses/excel")
    public String responsesExcel(@PathVariable String surveyId,
                                   @Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                                   BindingResult br,
                                   HttpServletRequest req,
                                   HttpServletResponse res,
                                   RedirectAttributes ra) throws IOException {
        String redirect = "/admin/system/survey/" + surveyId;
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:" + redirect;
        }
        Survey sv = service.getWithDetails(surveyId);
        if (sv == null) {
            ra.addFlashAttribute("error", "설문을 찾을 수 없습니다.");
            return "redirect:/admin/system/survey";
        }

        List<SurveyResponse> responses = responseService.listBySurvey(surveyId);
        List<SurveyQuestion> questions = sv.getQuestions() == null ? List.of() : sv.getQuestions();
        boolean anonymous = "Y".equalsIgnoreCase(sv.getAnonymousYn());

        // 헤더 — 고정 4컬럼 + 문항별 동적 컬럼
        String[] headers = new String[4 + questions.size()];
        headers[0] = "응답ID";
        headers[1] = "제출시각";
        headers[2] = "응답자";
        headers[3] = "IP";
        for (int i = 0; i < questions.size(); i++) {
            headers[4 + i] = "Q" + (i + 1) + ". " + nvl(questions.get(i).getQuestionText());
        }

        String excelFilename = excelWriter.write(res,
            "설문응답",
            "survey_" + surveyId + "_responses",
            headers,
            responses,
            (row, r) -> {
                row.createCell(0).setCellValue(nvl(r.getResponseId()));
                row.createCell(1).setCellValue(str(r.getSubmittedAt()));
                if (anonymous || r.getMemberId() == null) {
                    row.createCell(2).setCellValue("(익명)");
                } else {
                    String label = nvl(r.getMemberLoginId() != null ? r.getMemberLoginId() : r.getMemberId());
                    if (r.getMemberName() != null && !r.getMemberName().isBlank()) {
                        label = label + " (" + r.getMemberName() + ")";
                    }
                    row.createCell(2).setCellValue(label);
                }
                row.createCell(3).setCellValue(nvl(r.getClientIp()));

                // 문항별 답변 — 각 응답마다 별도 SELECT (작은 설문 가정)
                List<SurveyAnswer> answers = responseService.answersOf(r.getResponseId());
                Map<String, List<SurveyAnswer>> byQuestion = new HashMap<>();
                if (answers != null) {
                    for (SurveyAnswer a : answers) {
                        byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
                    }
                }
                for (int i = 0; i < questions.size(); i++) {
                    SurveyQuestion q = questions.get(i);
                    row.createCell(4 + i).setCellValue(formatAnswer(q, byQuestion.get(q.getQuestionId())));
                }
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_survey_response")
            .withTarget(surveyId)
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"format\":\"xlsx\",\"surveyId\":" + JsonUtils.quote(surveyId)
                + ",\"filename\":" + JsonUtils.quote(excelFilename)
                + ",\"count\":" + responses.size()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));

        log.info("===SURVEY_RESPONSE_XLSX ok surveyId={} count={} file={}",
            surveyId, responses.size(), excelFilename);
        return null;
    }

    /** 한 문항의 답변(들)을 1셀 문자열로 직렬화. 객관식 다중은 '|' 구분. */
    private static String formatAnswer(SurveyQuestion q, List<SurveyAnswer> answers) {
        if (answers == null || answers.isEmpty()) return "";
        String type = q.getQuestionType();
        if ("TEXT".equals(type) || "TEXTAREA".equals(type)) {
            return nvl(answers.get(0).getAnswerText());
        }
        if ("SCALE".equals(type)) {
            Integer n = answers.get(0).getAnswerNumber();
            return n == null ? "" : n.toString();
        }
        // RADIO / CHECKBOX / SELECT — option 텍스트 우선, 없으면 answerText
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < answers.size(); i++) {
            if (i > 0) sb.append('|');
            SurveyAnswer a = answers.get(i);
            String label = a.getOptionText() != null ? a.getOptionText() : a.getAnswerText();
            sb.append(nvl(label));
        }
        return sb.toString();
    }

    /** RFC 4180 — 큰따옴표/콤마/개행 포함 시 따옴표 감싸기 + 내부 따옴표 이스케이프. */
    private static String csv(String v) {
        if (v == null) return "";
        boolean needsQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
            || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!needsQuote) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    private static String str(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @GetMapping("/{surveyId}/responses/{responseId}")
    public String responseDetail(@PathVariable String surveyId,
                                   @PathVariable String responseId,
                                   Model model, RedirectAttributes ra) {
        Survey sv = service.getWithDetails(surveyId);
        SurveyResponse r = responseService.get(responseId);
        if (sv == null || r == null) {
            ra.addFlashAttribute("error", "응답을 찾을 수 없습니다.");
            return "redirect:/admin/system/survey/" + surveyId + "/responses";
        }
        List<SurveyAnswer> answers = responseService.answersOf(responseId);

        // questionId 별 그룹핑 — CHECKBOX 다중 답변을 한 행에 콤마 결합.
        // 설문 문항 순서 기준 Q# (1부터). 응답에만 등장하는 stale questionId 도 안전 매핑.
        List<Map<String, Object>> groupedAnswers = buildGroupedAnswers(sv, answers);

        model.addAttribute("survey", sv);
        model.addAttribute("response", r);
        model.addAttribute("groupedAnswers", groupedAnswers);
        return "admin/system/survey/response-detail";
    }

    /**
     * 설문 문항 순서대로 Q# 부여 + 같은 questionId 답변들을 콤마 결합.
     *
     * <p>각 행 구조: {@code {order:Integer, questionId, questionText, values:[String..],
     * joined:String}}. 객관식 옵션텍스트 우선, 없으면 자유 텍스트, 그것도 없으면 숫자.
     */
    private static List<Map<String, Object>> buildGroupedAnswers(Survey sv,
                                                                   List<SurveyAnswer> answers) {
        // 문항 순서 + 텍스트 인덱스
        Map<String, Integer> order = new LinkedHashMap<>();
        Map<String, String>  qTextByQid = new LinkedHashMap<>();
        if (sv.getQuestions() != null) {
            int i = 1;
            for (SurveyQuestion q : sv.getQuestions()) {
                order.put(q.getQuestionId(), i++);
                qTextByQid.put(q.getQuestionId(), q.getQuestionText());
            }
        }

        // questionId → 답변 표시 값 누적
        Map<String, List<String>> valuesByQid = new LinkedHashMap<>();
        Map<String, String>       textByQid   = new LinkedHashMap<>();
        if (answers != null) {
            int next = order.size() + 1;
            for (SurveyAnswer a : answers) {
                String qid = a.getQuestionId();
                if (qid == null) continue;
                if (!order.containsKey(qid)) {
                    order.put(qid, next++);
                    qTextByQid.put(qid, a.getQuestionText());
                }
                // 표시 값 결정 — option > text > number 순
                String v;
                if (a.getOptionText() != null && !a.getOptionText().isBlank()) {
                    v = a.getOptionText();
                } else if (a.getAnswerText() != null && !a.getAnswerText().isBlank()) {
                    v = a.getAnswerText();
                } else if (a.getAnswerNumber() != null) {
                    v = a.getAnswerNumber().toString();
                } else {
                    continue;  // 빈 답변 skip
                }
                valuesByQid.computeIfAbsent(qid, k -> new ArrayList<>()).add(v);
                textByQid.putIfAbsent(qid,
                    qTextByQid.getOrDefault(qid, a.getQuestionText()));
            }
        }

        // order 순서대로 List 빌드 — 응답 없는 문항은 skip (운영자가 응답 없음 표시를 원하면 별도)
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : order.entrySet()) {
            String qid = e.getKey();
            List<String> values = valuesByQid.get(qid);
            if (values == null || values.isEmpty()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order",        e.getValue());
            row.put("questionId",   qid);
            row.put("questionText", qTextByQid.getOrDefault(qid, textByQid.get(qid)));
            row.put("values",       values);
            row.put("joined",       String.join(", ", values));
            rows.add(row);
        }
        return rows;
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(value = "surveyMasterId", required = false) String surveyMasterId,
                              Model model) {
        if (!model.containsAttribute("form")) {
            SurveySaveForm form = new SurveySaveForm();
            form.setStatus("DRAFT");
            form.setUseYn("Y");
            form.setOneResponseYn("Y");
            form.setAnonymousYn("N");
            if (surveyMasterId != null && !surveyMasterId.isBlank()) {
                form.setSurveyMasterId(surveyMasterId);
            }
            model.addAttribute("form", form);
        }
        model.addAttribute("masters", listMasters());
        model.addAttribute("statuses", SurveyStatus.values());
        model.addAttribute("mode", "create");
        return "admin/system/survey/form";
    }

    @GetMapping("/{surveyId}/edit")
    public String editForm(@PathVariable String surveyId, Model model, RedirectAttributes ra) {
        Survey sv = service.getWithDetails(surveyId);
        if (sv == null) {
            ra.addFlashAttribute("error", "설문을 찾을 수 없습니다.");
            return "redirect:/admin/system/survey";
        }
        model.addAttribute("form", toForm(sv));
        model.addAttribute("existingQuestionsJson", serializeQuestions(sv.getQuestions()));
        model.addAttribute("masters", listMasters());
        model.addAttribute("statuses", SurveyStatus.values());
        model.addAttribute("mode", "edit");
        return "admin/system/survey/form";
    }

    /** 기존 문항을 form 화면 JS 가 시드로 사용하도록 JSON 으로 직렬화. */
    private static String serializeQuestions(List<SurveyQuestion> questions) {
        if (questions == null || questions.isEmpty()) return "[]";
        List<Map<String, Object>> arr = new ArrayList<>();
        for (SurveyQuestion q : questions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", q.getQuestionText());
            m.put("type", q.getQuestionType());
            m.put("required", q.getRequiredYn());
            if (q.getScaleMin() != null) m.put("scaleMin", q.getScaleMin());
            if (q.getScaleMax() != null) m.put("scaleMax", q.getScaleMax());
            List<Map<String, Object>> opts = new ArrayList<>();
            if (q.getOptions() != null) {
                for (SurveyOption o : q.getOptions()) {
                    Map<String, Object> om = new LinkedHashMap<>();
                    om.put("text",  o.getOptionText());
                    om.put("value", o.getOptionValue());
                    opts.add(om);
                }
            }
            m.put("options", opts);
            arr.add(m);
        }
        try {
            return JSON.writeValueAsString(arr);
        } catch (Exception ex) {
            return "[]";
        }
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") SurveySaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/survey/new";
        }
        try {
            String id = service.create(form);
            ra.addFlashAttribute("message", "설문을 등록했습니다.");
            String target = "/admin/system/survey/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/survey/new";
        } catch (Exception ex) {
            log.warn("SURVEY_CREATE error", ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/survey/new";
        }
    }

    @PostMapping("/{surveyId}")
    public String update(@PathVariable String surveyId,
                          @Valid @ModelAttribute("form") SurveySaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setSurveyId(surveyId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/survey/" + surveyId + "/edit";
        }
        try {
            service.update(form);
            ra.addFlashAttribute("message", "설문을 수정했습니다.");
            String target = "/admin/system/survey/" + surveyId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/survey/" + surveyId + "/edit";
        } catch (Exception ex) {
            log.warn("SURVEY_UPDATE error id={}", surveyId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/survey/" + surveyId + "/edit";
        }
    }

    @PostMapping("/{surveyId}/status")
    public String changeStatus(@PathVariable String surveyId,
                                @RequestParam("status") String status,
                                RedirectAttributes ra,
                                HttpServletResponse res) {
        String redirect = "/admin/system/survey/" + surveyId;
        try {
            service.changeStatus(surveyId, status);
            ra.addFlashAttribute("message", "상태를 변경했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("SURVEY_STATUS error", ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{surveyId}/use")
    public String toggleUse(@PathVariable String surveyId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/survey/" + surveyId;
        try {
            service.toggleUse(surveyId, active);
            ra.addFlashAttribute("message", active ? "사용 처리했습니다." : "사용중지했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{surveyId}/delete")
    public String delete(@PathVariable String surveyId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(surveyId);
            ra.addFlashAttribute("message", "설문을 삭제했습니다.");
            String target = "/admin/system/survey";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (Exception ex) {
            log.warn("SURVEY_DELETE error", ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/survey/" + surveyId;
        }
    }

    private List<SurveyMaster> listMasters() {
        SurveyMasterSearch ms = new SurveyMasterSearch();
        ms.setPage(1);
        ms.setPageSizeUnbounded(500);
        ms.setUseYn("Y");
        return masterService.search(ms);
    }

    private SurveySaveForm toForm(Survey sv) {
        SurveySaveForm f = new SurveySaveForm();
        f.setSurveyId(sv.getSurveyId());
        f.setSurveyMasterId(sv.getSurveyMasterId());
        f.setSurveyTitle(sv.getSurveyTitle());
        f.setSurveyDescription(sv.getSurveyDescription());
        f.setStartAt(sv.getStartAt());
        f.setEndAt(sv.getEndAt());
        f.setStatus(sv.getStatus());
        f.setAnonymousYn(sv.getAnonymousYn());
        f.setOneResponseYn(sv.getOneResponseYn());
        f.setUseYn(sv.getUseYn());
        // questionsJson 은 form 화면에서 JS 가 question 카드들을 직렬화해 채움
        return f;
    }
}
