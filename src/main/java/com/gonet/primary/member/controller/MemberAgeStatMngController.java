package com.gonet.primary.member.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.primary.member.dto.AgeGroupStat;
import com.gonet.primary.member.service.MemberAgeStatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 회원 연령대별 통계 — {@code /admin/system/member/stat/age}.
 *
 * <p>birth_year 4자리 평문 컬럼 (PIPA 일반 개인정보, 암호화 의무 X) 기반.
 * 연령대는 통계 시점에 동적 계산 — {@code FLOOR((NOW().year - birth_year) / 10) * 10}.
 *
 * <p>매년 새해가 되면 같은 회원도 다른 연령대로 자동 이동 (정확한 현재 연령대 반영).
 */
@Controller
@RequestMapping("/admin/system/member/stat/age")
public class MemberAgeStatMngController {

    private static final Logger log = LoggerFactory.getLogger(MemberAgeStatMngController.class);
    // script 블록 삽입 안전(HTML 이스케이프) — 통계 페이로드 방어심층.
    private static final ObjectMapper JSON = com.gonet.common.util.HtmlSafeJson.mapper();

    private final MemberAgeStatService service;

    public MemberAgeStatMngController(MemberAgeStatService service) {
        this.service = service;
    }

    @GetMapping
    public String dashboard(@RequestParam(value = "siteId",  required = false) String siteId,
                             @RequestParam(value = "scope",  defaultValue = "active") String scope,
                             Model model) {
        boolean includeDormant = "all".equalsIgnoreCase(scope);
        List<AgeGroupStat> rows = includeDormant
            ? service.activeAndDormantByAgeGroup(siteId)
            : service.activeByAgeGroup(siteId);

        // labels + data + 총합 계산
        List<String> labels = new ArrayList<>();
        List<Long>   data   = new ArrayList<>();
        long total = 0;
        for (AgeGroupStat s : rows) {
            labels.add(formatLabel(s.getAgeGroupStart()));
            data.add(s.getMemberCount());
            total += s.getMemberCount();
        }

        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("labels", labels);
        chart.put("data",   data);
        String chartJson;
        try {
            chartJson = JSON.writeValueAsString(chart);
        } catch (JsonProcessingException ex) {
            log.warn("AGE_STAT json err", ex);
            chartJson = "{}";
        }

        model.addAttribute("rows",       rows);
        model.addAttribute("total",      total);
        model.addAttribute("siteId",     siteId);
        model.addAttribute("scope",      scope);
        model.addAttribute("currentYear", Year.now().getValue());
        model.addAttribute("chartJson",  chartJson);
        return "admin/system/member/stat/age";
    }

    /** {@code 20} → "20대", null → "미입력". */
    private static String formatLabel(Integer start) {
        if (start == null) return "미입력";
        if (start >= 80) return "80대+";
        return start + "대";
    }
}
