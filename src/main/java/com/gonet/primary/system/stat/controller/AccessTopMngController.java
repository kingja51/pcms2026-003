package com.gonet.primary.system.stat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.logging.access.dto.AccessTopRow;
import com.gonet.logging.access.service.AccessStatBatchService;
import com.gonet.logging.access.service.AccessTopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 접근 TOP-20 통계 화면 — 11 차트 (보안 + 통계 + 인프라).
 *
 * <p>URL: {@code /admin/system/access-top20}
 * <p>권한: STAFF 이상 (tb_role_url_access /admin/** 와일드카드 가드)
 * <p>데이터 소스: log_access + log_file_download 직접 조회 (집계 테이블 미사용 — 차원 불일치).
 *
 * <p>실시간 갱신:
 * <ul>
 *   <li>매 10분 자동 reload — meta refresh 600초</li>
 *   <li>"실시간 적용" 버튼 — POST /refresh → 오늘 분 stat_* 재집계 후 redirect.
 *       본 화면은 log_access 원본을 직접 보지만, AccessLogger 가 비동기라
 *       매우 짧은 지연(수초) 후 commit. 화면 reload 만으로 사실상 실시간.</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/system/access-top20")
public class AccessTopMngController {

    private static final Logger log = LoggerFactory.getLogger(AccessTopMngController.class);

    /** 모든 차트 TOP 행 수. */
    private static final int LIMIT = 20;

    /** 평균 응답속도 차트의 최소 호출 횟수 — 우연히 1회 느린 케이스 배제. */
    private static final int SLOW_MIN_CALLS = 5;

    /** 화면 자동 새로고침 주기(초) — access-stat 과 동일 1800초 = 30분. */
    private static final int AUTO_REFRESH_SECONDS = 1800;

    private final AccessTopService       topService;
    private final ObjectMapper           objectMapper;
    private final AccessStatBatchService batchService;

    public AccessTopMngController(AccessTopService topService,
                                    ObjectMapper objectMapper,
                                    AccessStatBatchService batchService) {
        this.topService   = topService;
        this.objectMapper = objectMapper;
        this.batchService = batchService;
    }

    @GetMapping
    public String dashboard(@RequestParam(value = "period", defaultValue = "week") String period,
                             @RequestParam(value = "from",   required = false) String fromStr,
                             @RequestParam(value = "to",     required = false) String toStr,
                             Model model) {
        LocalDate to   = parseOr(toStr,   today());
        LocalDate from = parseOr(fromStr, defaultFrom(to, period));

        Map<String, List<AccessTopRow>> tops =
            topService.all(from, to, LIMIT, SLOW_MIN_CALLS);

        Map<String, Object> charts = buildChartPayloads(tops);

        model.addAttribute("period",         period);
        model.addAttribute("from",           from);
        model.addAttribute("to",             to);
        model.addAttribute("limit",          LIMIT);
        model.addAttribute("slowMinCalls",   SLOW_MIN_CALLS);
        model.addAttribute("tops",           tops);
        model.addAttribute("chartsJson",     toJson(charts));
        model.addAttribute("loadedAt",       LocalDateTime.now());
        model.addAttribute("autoRefreshSec", AUTO_REFRESH_SECONDS);
        return "admin/system/access-top20/dashboard";
    }

    /** 11 차트별 {labels[], values[], extras[]} 직렬화. */
    private Map<String, Object> buildChartPayloads(Map<String, List<AccessTopRow>> tops) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, List<AccessTopRow>> e : tops.entrySet()) {
            List<AccessTopRow> rows = e.getValue();
            root.put(e.getKey(), Map.of(
                "labels", rows.stream().map(r -> nvl(r.getLabel())).toList(),
                "values", rows.stream().map(r -> r.getValue() == null ? 0L : r.getValue()).toList(),
                "extras", rows.stream().map(AccessTopRow::getValueExtra).toList()
            ));
        }
        return root;
    }

    /**
     * 실시간 적용 — access-stat 과 동일 패턴. 오늘 분 stat_* 재집계 후 redirect.
     * 본 화면은 log_access 원본을 직접 조회하므로 stat_ 재집계가 필수는 아니지만,
     * 화면 reload 자체가 새 commit 을 반영하는 효과 + access-stat UX 일관성 유지.
     */
    @PostMapping("/refresh")
    public String refresh(@RequestParam(value = "period", defaultValue = "week") String period,
                           @RequestParam(value = "from",   required = false) String fromStr,
                           @RequestParam(value = "to",     required = false) String toStr,
                           RedirectAttributes ra) {
        LocalDate today = today();
        try {
            int rows = batchService.aggregateFor(today);
            log.info("===ACCESS_TOP_LIVE_REFRESH_OK date={} rows={}", today, rows);
            ra.addFlashAttribute("message",
                "오늘(" + today + ") 분을 즉시 반영했습니다. (" + rows + " 차원 행)");
        } catch (Exception ex) {
            log.warn("ACCESS_TOP_LIVE_REFRESH_FAIL date={} reason={}", today, ex.getMessage());
            ra.addFlashAttribute("error", "재집계 중 오류: " + ex.getMessage());
        }
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/admin/system/access-top20")
            .queryParam("period", period == null ? "week" : period);
        if (fromStr != null && !fromStr.isBlank()) b.queryParam("from", fromStr.trim());
        if (toStr   != null && !toStr.isBlank())   b.queryParam("to",   toStr.trim());
        return "redirect:" + b.build().toUriString();
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (JsonProcessingException ex) {
            log.warn("ACCESS_TOP_JSON_FAIL", ex);
            return "{}";
        }
    }

    private static LocalDate today() { return LocalDate.now(); }

    private static LocalDate defaultFrom(LocalDate to, String period) {
        return switch (period == null ? "" : period.toLowerCase()) {
            case "day"   -> to;
            case "week"  -> to.minusDays(6);
            case "month" -> to.minusDays(29);
            case "year"  -> to.minusDays(364);
            default       -> to.minusDays(6);
        };
    }

    private static LocalDate parseOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return LocalDate.parse(raw.trim()); }
        catch (Exception ex) {
            log.warn("ACCESS_TOP_DATE_PARSE_FAIL raw={} fallback={}", raw, fallback);
            return fallback;
        }
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
