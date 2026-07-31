package com.gonet.primary.system.stat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.logging.access.dto.AccessStatDaily;
import com.gonet.logging.access.dto.AccessStatUriDaily;
import com.gonet.logging.access.dto.DailyVisitStat;
import com.gonet.logging.access.service.AccessStatBatchService;
import com.gonet.primary.system.site.dto.Site;
import com.gonet.primary.system.site.dto.SiteSearch;
import com.gonet.primary.system.site.service.SiteService;
import com.gonet.primary.system.stat.service.AccessStatViewService;
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
 * 접근 로그 통계 화면 — 5개 통계 + 5종 기간 토글.
 *
 * <p>URL: {@code /admin/system/access-stat}
 * <p>권한: STAFF 이상 (tb_role_url_access 규칙으로 보호)
 * <p>데이터 소스: stat_access_daily / stat_access_uri_daily 만 — log_access 직접 조회 안 함.
 */
@Controller
@RequestMapping("/admin/system/access-stat")
public class AccessStatMngController {

    private static final Logger log = LoggerFactory.getLogger(AccessStatMngController.class);

    /**
     * 통계 base date — 오늘까지 포함 (10분마다 재집계 됨).
     * 어제 분은 02:00 배치, 오늘 분은 매 10분 cron 으로 갱신.
     */
    private static LocalDate today() {
        return LocalDate.now();
    }

    /** 화면 자동 새로고침 주기(초) — 30분 = 1800초. */
    private static final int AUTO_REFRESH_SECONDS = 1800;

    private final AccessStatViewService  statService;
    private final SiteService            siteService;
    private final ObjectMapper           objectMapper;
    private final AccessStatBatchService batchService;

    public AccessStatMngController(AccessStatViewService statService,
                                    SiteService siteService,
                                    ObjectMapper objectMapper,
                                    AccessStatBatchService batchService) {
        this.statService  = statService;
        this.siteService  = siteService;
        this.objectMapper = objectMapper;
        this.batchService = batchService;
    }

    @GetMapping
    public String dashboard(@RequestParam(value = "period",  defaultValue = "week") String period,
                             @RequestParam(value = "siteId",  required = false) String siteId,
                             @RequestParam(value = "from",    required = false) String fromStr,
                             @RequestParam(value = "to",      required = false) String toStr,
                             Model model) {
        // ── 기간 결정 ───────────────────────────────────────────────
        // 오늘까지 포함 — 매 10분 cron 이 stat_access_daily 의 오늘 분을 재집계
        LocalDate to   = parseOr(toStr,   today());
        LocalDate from = parseOr(fromStr, defaultFrom(to, period));

        // ── 통계 데이터 (stat_access_daily 큐브) ─────────────────
        Map<String, Object>      summary       = statService.summary(from, to, siteId);
        List<AccessStatDaily>    series        = statService.dailySeries(from, to, siteId);
        List<AccessStatDaily>    siteShare     = statService.siteShare(from, to);
        List<AccessStatDaily>    userTypeShare = statService.userTypeShare(from, to, siteId);
        List<AccessStatDaily>    hourly        = statService.hourlyPattern(from, to, siteId);
        List<AccessStatUriDaily> topPages      = statService.topPages(from, to, siteId, 20);

        // ── 통계 데이터 (stat_daily_visit 표준) ─────────────────
        List<DailyVisitStat> dailyVisit       = statService.dailyVisit(from, to, siteId);
        List<DailyVisitStat> dailyVisitBySite = statService.dailyVisitBySite(from, to);

        // ── 사이트 selector 옵션 ───────────────────────────────────
        SiteSearch ss = new SiteSearch();
        ss.setPage(1); ss.setPageSize(200);
        List<Site> sites = siteService.search(ss);

        // ── Chart.js 용 JSON 페이로드 ──────────────────────────
        Map<String, Object> charts = buildChartPayloads(
            series, siteShare, userTypeShare, hourly, topPages, dailyVisit);

        model.addAttribute("period",            period);
        model.addAttribute("from",              from);
        model.addAttribute("to",                to);
        model.addAttribute("siteId",            siteId);
        model.addAttribute("sites",             sites);
        model.addAttribute("summary",           summary);
        model.addAttribute("series",            series);
        model.addAttribute("siteShare",         siteShare);
        model.addAttribute("userTypeShare",     userTypeShare);
        model.addAttribute("hourly",            hourly);
        model.addAttribute("topPages",          topPages);
        model.addAttribute("dailyVisit",        dailyVisit);
        model.addAttribute("dailyVisitBySite",  dailyVisitBySite);
        // 사이트별 누적 KPI 합계 — Thymeleaf 에서 직접 stream sum 안 되므로 미리 계산
        long totalVisit  = dailyVisitBySite.stream().mapToLong(DailyVisitStat::getVisitCount).sum();
        long totalUnique = dailyVisitBySite.stream().mapToLong(DailyVisitStat::getUniqueCount).sum();
        long totalPv     = dailyVisitBySite.stream().mapToLong(DailyVisitStat::getPvCount).sum();
        model.addAttribute("dailyVisitTotalVisit",  totalVisit);
        model.addAttribute("dailyVisitTotalUnique", totalUnique);
        model.addAttribute("dailyVisitTotalPv",     totalPv);
        model.addAttribute("chartsJson",        toJson(charts));
        // 실시간 갱신 메타 — 화면이 N초마다 자동 reload + 마지막 갱신 시각 표시
        model.addAttribute("loadedAt",          LocalDateTime.now());
        model.addAttribute("autoRefreshSec",    AUTO_REFRESH_SECONDS);
        return "admin/system/access-stat/dashboard";
    }

    /**
     * Chart.js 가 렌더링할 데이터셋을 한 번에 묶어 JSON 으로 직렬화.
     * - inline script 안에서 {@code window.STATS = JSON.parse('...')} 로 받음.
     */
    private Map<String, Object> buildChartPayloads(List<AccessStatDaily> series,
                                                    List<AccessStatDaily> siteShare,
                                                    List<AccessStatDaily> userTypeShare,
                                                    List<AccessStatDaily> hourly,
                                                    List<AccessStatUriDaily> topPages,
                                                    List<DailyVisitStat> dailyVisit) {
        Map<String, Object> root = new LinkedHashMap<>();

        // ① 일별 PV/UV 추이 — line
        root.put("series", Map.of(
            "labels", series.stream().map(r -> String.valueOf(r.getStatDate())).toList(),
            "pv",     series.stream().map(AccessStatDaily::getPv).toList(),
            "uv",     series.stream().map(AccessStatDaily::getUv).toList(),
            "users",  series.stream().map(AccessStatDaily::getUniqueUsers).toList()
        ));

        // ② 사이트별 트래픽 — donut
        root.put("siteShare", Map.of(
            "labels", siteShare.stream().map(r ->
                r.getSiteCode() == null ? "(unknown)" : r.getSiteCode()).toList(),
            "pv",     siteShare.stream().map(AccessStatDaily::getPv).toList()
        ));

        // ③ 사용자 유형별 — donut
        root.put("userTypeShare", Map.of(
            "labels", userTypeShare.stream().map(AccessStatDaily::getUserType).toList(),
            "pv",     userTypeShare.stream().map(AccessStatDaily::getPv).toList()
        ));

        // ④ 시간대별 — bar (24)
        long[] hourlyPv = new long[24];
        for (AccessStatDaily r : hourly) {
            if (r.getBucketHour() != null && r.getBucketHour() >= 0 && r.getBucketHour() < 24) {
                hourlyPv[r.getBucketHour()] = r.getPv();
            }
        }
        root.put("hourly", Map.of(
            "labels", java.util.stream.IntStream.range(0, 24)
                .mapToObj(i -> i + "시").toList(),
            "pv",     java.util.Arrays.stream(hourlyPv).boxed().toList()
        ));

        // ⑤ 인기 페이지 TOP — horizontal bar
        root.put("topPages", Map.of(
            "labels", topPages.stream().map(AccessStatUriDaily::getRequestUri).toList(),
            "pv",     topPages.stream().map(AccessStatUriDaily::getPv).toList()
        ));

        // ⑥ stat_daily_visit — multi-line (visit/unique/pv 3 시리즈)
        root.put("dailyVisit", Map.of(
            "labels", dailyVisit.stream().map(r -> String.valueOf(r.getStatDate())).toList(),
            "visit",  dailyVisit.stream().map(DailyVisitStat::getVisitCount).toList(),
            "unique", dailyVisit.stream().map(DailyVisitStat::getUniqueCount).toList(),
            "pv",     dailyVisit.stream().map(DailyVisitStat::getPvCount).toList()
        ));

        return root;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            log.warn("ACCESS_STAT_JSON_FAIL", ex);
            return "{}";
        }
    }

    /**
     * 실시간 적용 — 사용자 클릭 즉시 오늘 분 재집계 후 같은 필터로 redirect.
     *
     * <p>매 10분 cron 을 기다리지 않고 지금 바로 stat_access_daily / stat_daily_visit /
     * stat_access_uri_daily 의 오늘 행을 재계산. 멱등이라 cron 과 동시 실행되어도 안전.
     *
     * <p>응답: 같은 dashboard 로 redirect — period/siteId 필터 보존.
     * 클라이언트의 meta refresh 카운트다운도 자동으로 600 초로 reset 됨 (페이지 reload).
     */
    @PostMapping("/refresh")
    public String refresh(@RequestParam(value = "period", defaultValue = "week") String period,
                           @RequestParam(value = "siteId", required = false) String siteId,
                           RedirectAttributes ra) {
        LocalDate today = today();
        try {
            int rows = batchService.aggregateFor(today);
            log.info("===ACCESS_STAT_LIVE_REFRESH_OK date={} rows={}", today, rows);
            ra.addFlashAttribute("message",
                "오늘(" + today + ") 분을 즉시 재집계했습니다. (" + rows + " 차원 행)");
        } catch (Exception ex) {
            log.warn("ACCESS_STAT_LIVE_REFRESH_FAIL date={} reason={}", today, ex.getMessage());
            ra.addFlashAttribute("error", "재집계 중 오류: " + ex.getMessage());
        }
        // 같은 필터로 dashboard 로 복귀
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/admin/system/access-stat")
            .queryParam("period", period == null ? "week" : period);
        if (siteId != null && !siteId.isBlank()) {
            b.queryParam("siteId", siteId.trim());
        }
        return "redirect:" + b.build().toUriString();
    }

    /** period 토글에 따른 기본 from 결정. to(=어제) 기준으로 거꾸로 산정. */
    private static LocalDate defaultFrom(LocalDate to, String period) {
        return switch (period == null ? "" : period.toLowerCase()) {
            case "day"   -> to;                       // 어제 하루
            case "week"  -> to.minusDays(6);          // 7일
            case "month" -> to.minusDays(29);         // 30일
            case "year"  -> to.minusDays(364);        // 365일
            default       -> to.minusDays(6);          // week
        };
    }

    private static LocalDate parseOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return LocalDate.parse(raw.trim()); }
        catch (Exception ex) {
            log.warn("ACCESS_STAT_DATE_PARSE_FAIL raw={} fallback={}", raw, fallback);
            return fallback;
        }
    }
}
