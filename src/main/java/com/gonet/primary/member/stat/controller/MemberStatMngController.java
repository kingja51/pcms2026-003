package com.gonet.primary.member.stat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.primary.member.stat.dto.AgeBucketCount;
import com.gonet.primary.member.stat.dto.DailyMemberStat;
import com.gonet.primary.member.stat.dto.DiVerifiedShare;
import com.gonet.primary.member.stat.dto.GenderCount;
import com.gonet.primary.member.stat.dto.MemberCountByTable;
import com.gonet.primary.member.stat.dto.ProviderCount;
import com.gonet.primary.member.stat.dto.SiteMemberCount;
import com.gonet.primary.member.stat.dto.WithdrawStatusCount;
import com.gonet.primary.member.stat.service.MemberStatService;
import com.gonet.primary.system.site.dto.Site;
import com.gonet.primary.system.site.dto.SiteSearch;
import com.gonet.primary.system.site.service.SiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 회원 통계 화면 — 라이프사이클 + 인구통계 + OAuth + 추이.
 *
 * <p>URL: {@code /admin/system/member-stat}
 * <p>권한: STAFF 이상 (tb_role_url_access).
 * <p>{@code AccessStatMngController} 와 동일한 5종 기간 토글(day/week/month/year/전체) 채택.
 */
@Controller
@RequestMapping("/admin/system/member-stat")
public class MemberStatMngController {

    private static final Logger log = LoggerFactory.getLogger(MemberStatMngController.class);

    private static final int AUTO_REFRESH_SECONDS = 1800;

    private final MemberStatService statService;
    private final SiteService       siteService;
    private final ObjectMapper      objectMapper;

    public MemberStatMngController(MemberStatService statService,
                                    SiteService siteService,
                                    ObjectMapper objectMapper) {
        this.statService  = statService;
        this.siteService  = siteService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String dashboard(@RequestParam(value = "period", defaultValue = "month") String period,
                             @RequestParam(value = "siteId", required = false) String siteId,
                             @RequestParam(value = "from",   required = false) String fromStr,
                             @RequestParam(value = "to",     required = false) String toStr,
                             Model model) {

        LocalDate to   = parseOr(toStr,   LocalDate.now());
        LocalDate from = parseOr(fromStr, defaultFrom(to, period));

        // 1) 라이프사이클 3 테이블 카운트
        List<MemberCountByTable> lifecycle = statService.countByLifecycleTable(siteId);
        Map<String, Long> lifecycleMap = new LinkedHashMap<>();
        for (MemberCountByTable r : lifecycle) {
            lifecycleMap.put(r.getTableName(), r.getTotal());
        }

        // 2) 연령대 / 성별
        List<AgeBucketCount> age    = statService.ageDistribution(siteId);
        List<GenderCount>    gender = statService.genderDistribution(siteId);

        // 3) OAuth provider / DI
        List<ProviderCount> providers = statService.providerDistribution(siteId);
        DiVerifiedShare     di        = statService.diVerifiedShare(siteId);

        // 4) 탈퇴 사유
        List<WithdrawStatusCount> withdrawReasons = statService.withdrawStatusDistribution(siteId);

        // 5) 일별 추이
        List<DailyMemberStat> trend = statService.dailyTrend(siteId, from, to);

        // 6) 사이트별 회원수 — 전체 모드(siteId 미지정) 에서만 의미. siteId 있으면 빈 리스트 시각.
        List<SiteMemberCount> bySite = (siteId == null || siteId.isBlank())
            ? statService.memberCountBySite()
            : List.of();

        // 사이트 selector
        SiteSearch ss = new SiteSearch();
        ss.setPage(1); ss.setPageSize(200);
        List<Site> sites = siteService.search(ss);

        // Chart.js payload
        Map<String, Object> charts = buildChartPayloads(
            lifecycleMap, age, gender, providers, di, withdrawReasons, trend, bySite);

        model.addAttribute("period",          period);
        model.addAttribute("from",            from);
        model.addAttribute("to",              to);
        model.addAttribute("siteId",          siteId);
        model.addAttribute("sites",           sites);
        model.addAttribute("lifecycleMap",    lifecycleMap);
        model.addAttribute("age",             age);
        model.addAttribute("gender",          gender);
        model.addAttribute("providers",       providers);
        model.addAttribute("di",              di);
        model.addAttribute("withdrawReasons", withdrawReasons);
        model.addAttribute("trend",           trend);
        model.addAttribute("bySite",          bySite);
        model.addAttribute("chartsJson",      toJson(charts));
        model.addAttribute("loadedAt",        LocalDateTime.now());
        model.addAttribute("autoRefreshSec",  AUTO_REFRESH_SECONDS);
        return "admin/system/member-stat/dashboard";
    }

    private Map<String, Object> buildChartPayloads(Map<String, Long> lifecycle,
                                                    List<AgeBucketCount> age,
                                                    List<GenderCount> gender,
                                                    List<ProviderCount> providers,
                                                    DiVerifiedShare di,
                                                    List<WithdrawStatusCount> withdrawReasons,
                                                    List<DailyMemberStat> trend,
                                                    List<SiteMemberCount> bySite) {
        Map<String, Object> root = new LinkedHashMap<>();

        // ① 라이프사이클 3 카드 — bar
        root.put("lifecycle", Map.of(
            "labels", List.of("활성", "휴면", "탈퇴(보존중)"),
            "total",  List.of(
                lifecycle.getOrDefault("tb_member",          0L),
                lifecycle.getOrDefault("tb_member_dormant",  0L),
                lifecycle.getOrDefault("tb_member_withdraw", 0L)
            )
        ));

        // ② 연령대 — bar
        root.put("age", Map.of(
            "labels", age.stream().map(MemberStatMngController::ageLabel).toList(),
            "total",  age.stream().map(AgeBucketCount::getTotal).toList()
        ));

        // ③ 성별 — donut
        root.put("gender", Map.of(
            "labels", gender.stream().map(MemberStatMngController::genderLabel).toList(),
            "total",  gender.stream().map(GenderCount::getTotal).toList()
        ));

        // ④ OAuth provider — donut
        root.put("provider", Map.of(
            "labels", providers.stream().map(ProviderCount::getProvider).toList(),
            "total",  providers.stream().map(ProviderCount::getTotal).toList()
        ));

        // ⑤ DI 인증 share — donut
        root.put("di", Map.of(
            "labels", List.of("실명인증", "미인증"),
            "total",  List.of(di.getVerified(), di.getUnverified())
        ));

        // ⑥ 탈퇴 사유 — bar
        root.put("withdrawReasons", Map.of(
            "labels", withdrawReasons.stream()
                .map(r -> withdrawLabel(r.getWithdrawStatus())).toList(),
            "total",  withdrawReasons.stream().map(WithdrawStatusCount::getTotal).toList()
        ));

        // ⑦ 일별 추이 — multi-line (가입/휴면/탈퇴)
        root.put("trend", Map.of(
            "labels",  trend.stream().map(r -> String.valueOf(r.getStatDate())).toList(),
            "join",    trend.stream().map(DailyMemberStat::getJoinCount).toList(),
            "dormant", trend.stream().map(DailyMemberStat::getDormantCount).toList(),
            "withdraw", trend.stream().map(DailyMemberStat::getWithdrawCount).toList()
        ));

        // ⑧ 사이트별 회원수 — horizontal bar (전체 모드 only)
        root.put("bySite", Map.of(
            "labels", bySite.stream().map(r ->
                r.getSiteCode() == null ? "(unknown)" : r.getSiteCode()).toList(),
            "total",  bySite.stream().map(SiteMemberCount::getTotal).toList()
        ));

        return root;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            log.warn("MEMBER_STAT_JSON_FAIL", ex);
            return "{}";
        }
    }

    private static String ageLabel(AgeBucketCount r) {
        String k = r.getAgeBucket();
        if (k == null) return "기타";
        return switch (k) {
            case "10"      -> "10대";
            case "20"      -> "20대";
            case "30"      -> "30대";
            case "40"      -> "40대";
            case "50"      -> "50대";
            case "60+"     -> "60대+";
            case "CHILD"   -> "10세 미만";
            case "UNKNOWN" -> "미입력";
            default        -> k;
        };
    }

    private static String genderLabel(GenderCount r) {
        String k = r.getGender();
        if (k == null) return "미입력";
        return switch (k) {
            case "M"       -> "남성";
            case "F"       -> "여성";
            case "UNKNOWN" -> "미입력";
            default        -> k;
        };
    }

    private static String withdrawLabel(String status) {
        if (status == null) return "기타";
        return switch (status) {
            case "USER_REQUEST"    -> "사용자 요청";
            case "ADMIN_FORCE"     -> "관리자 강제";
            case "DORMANT_EXPIRED" -> "휴면 만료";
            case "UNKNOWN"         -> "미입력";
            default                 -> status;
        };
    }

    private static LocalDate defaultFrom(LocalDate to, String period) {
        return switch (period == null ? "" : period.toLowerCase()) {
            case "day"   -> to;
            case "week"  -> to.minusDays(6);
            case "month" -> to.minusDays(29);
            case "year"  -> to.minusDays(364);
            case "all"   -> to.minusYears(10);     // 사실상 전체 — 가입 시점 가장 오래된 데이터까지 포섭
            default       -> to.minusDays(29);
        };
    }

    private static LocalDate parseOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return LocalDate.parse(raw.trim()); }
        catch (Exception ex) {
            log.warn("MEMBER_STAT_DATE_PARSE_FAIL raw={} fallback={}", raw, fallback);
            return fallback;
        }
    }
}
