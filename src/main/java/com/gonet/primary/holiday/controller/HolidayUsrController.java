package com.gonet.primary.holiday.controller;

import com.gonet.common.calendar.CalendarMonth;
import com.gonet.common.dto.PageResponse;
import com.gonet.primary.holiday.dto.Holiday;
import com.gonet.primary.holiday.dto.HolidaySearch;
import com.gonet.primary.holiday.service.HolidayService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공휴일 사용자 화면 — 목록 / 상세 / 캘린더. 누구나 접근.
 *
 * <p>tb_holiday 는 전역 공통 테이블이므로 사이트 컨텍스트와 무관.
 */
@Controller
@RequestMapping("/holiday")
public class HolidayUsrController {

    private final HolidayService service;

    public HolidayUsrController(HolidayService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@ModelAttribute("search") HolidaySearch search, Model model) {
        // 사용자 화면은 useYn='Y' 만 (year 는 사용자 선택값 그대로 — 비우면 전체)
        search.setUseYn("Y");
        search.setSortDir("ASC"); // 사용자 — 달력 순서대로

        List<Holiday> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "front/holiday/list";
    }

    @GetMapping("/{holidayId}")
    public String detail(@PathVariable String holidayId, Model model) {
        Holiday h = service.get(holidayId);
        if (h == null || !"Y".equals(h.getUseYn())) {
            model.addAttribute("error", "공휴일을 찾을 수 없거나 사용중지 상태입니다.");
            return "front/holiday/detail";
        }
        model.addAttribute("holiday", h);
        return "front/holiday/detail";
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) Integer year,
                            @RequestParam(required = false) Integer month,
                            Model model) {
        YearMonth ym = (year != null && month != null)
            ? YearMonth.of(year, month) : YearMonth.now();

        CalendarMonth cm = new CalendarMonth(ym, LocalDate.now());

        Map<String, List<Holiday>> byDate = new LinkedHashMap<>();
        List<Holiday> rows = service.findInRange(cm.getGridStart(), cm.getGridEnd());
        for (Holiday h : rows) {
            byDate.computeIfAbsent(h.getHolidayDate().toString(),
                                     k -> new java.util.ArrayList<>()).add(h);
        }

        model.addAttribute("cm", cm);
        model.addAttribute("byDate", byDate);
        return "front/holiday/calendar";
    }
}
