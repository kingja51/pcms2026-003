package com.gonet.primary.schedule.controller;

import com.gonet.common.calendar.CalendarMonth;
import com.gonet.common.calendar.CalendarWeek;
import com.gonet.common.dto.PageResponse;
import com.gonet.primary.holiday.dto.Holiday;
import com.gonet.primary.holiday.service.HolidayService;
import com.gonet.primary.schedule.dto.Schedule;
import com.gonet.primary.schedule.dto.ScheduleMaster;
import com.gonet.primary.schedule.dto.ScheduleSearch;
import com.gonet.primary.schedule.service.ScheduleMasterService;
import com.gonet.primary.schedule.service.ScheduleService;
import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextResolver;
import com.gonet.primary.system.site.service.SiteContextService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 일정 사용자 화면 — {@code /schedule/{scheduleMasterId}} 스코프.
 *
 * <p>모든 라우트는 마스터 단위로 스코프됨. 사이트 컨텍스트는 master.site_id 로 추적.
 * 캘린더 화면은 같은 master 의 일정 + 전역 공휴일을 함께 그린다.
 *
 * <p>{@link #injectSiteContext} 가 master / siteId / siteCode / menuId / scheduleMasterId 5종을
 * 매 요청마다 한 번에 model 에 주입 — 핸들러 메서드는 도메인 로직만 담당.
 */
@Slf4j
@Controller
@RequestMapping("/schedule/{scheduleMasterId}")
public class ScheduleUsrController {

    private final ScheduleService        service;
    private final ScheduleMasterService  masterService;
    private final HolidayService         holidayService;
    private final SiteContextResolver siteContextResolver;

    public ScheduleUsrController(ScheduleService service,
                                 ScheduleMasterService masterService,
                                 HolidayService holidayService, SiteContextResolver siteContextResolver) {
        this.service = service;
        this.masterService = masterService;
        this.holidayService = holidayService;
        this.siteContextResolver = siteContextResolver;
    }

    /**
     * 모든 핸들러 진입 직전 1회 — master 단건 조회 후 siteId / siteCode / menuId 를 model 에 주입.
     * tb_schedule_master 가 site_id / menu_id 를 직접 보유하므로 단순 select 1번이면 끝.
     * SiteContextInterceptor.postHandle 이 model.menuId 를 SiteContext 와 동기화한다.
     */
    @ModelAttribute
    public void injectSiteContext(@PathVariable(required = false) String scheduleMasterId,
                                  HttpServletRequest req, Model model) {
        if (scheduleMasterId == null || scheduleMasterId.isBlank()) return;
        ScheduleMaster master = masterService.get(scheduleMasterId);
        if (master == null) return;

        model.addAttribute("master", master);
        model.addAttribute("scheduleMasterId", master.getScheduleMasterId());
        model.addAttribute("menuId", master.getMenuId());
        model.addAttribute("siteId", master.getSiteId());

        // 사이트 컨텍스트 주입 + session sticky 동시 — 평면 URL 폴백이 동일 사이트로 유지.
        siteContextResolver.injectByCodeAndStick(master.getSiteCode(), req, model);
    }

    @GetMapping
    public String list(@PathVariable String scheduleMasterId,
                        @ModelAttribute("master") ScheduleMaster master,
                        @ModelAttribute("search") ScheduleSearch search,
                        Model model) {
        if (master == null) {
            model.addAttribute("page", PageResponse.of(List.of(), 1, 20, 0));
            model.addAttribute("groupedSchedules", new LinkedHashMap<String, List<Schedule>>());
            return "front/schedule/list";
        }
        search.setScheduleMasterId(scheduleMasterId);
        search.setUseYn("Y");
        search.setSortDir("ASC"); // 사용자 — 시간 순(예정된 일정 순서대로)

        List<Schedule> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));

        // 년-월 단위 그룹핑 — DB 정렬 순서(start_at ASC) 보존
        Map<String, List<Schedule>> grouped = new LinkedHashMap<>();
        java.time.format.DateTimeFormatter ymFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
        for (Schedule sc : rows) {
            String key = sc.getStartAt() != null ? sc.getStartAt().format(ymFmt) : "-";
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(sc);
        }
        model.addAttribute("groupedSchedules", grouped);
        return "front/schedule/list";
    }

    @GetMapping("/calendar")
    public String calendar(@PathVariable String scheduleMasterId,
                            @RequestParam(required = false) Integer year,
                            @RequestParam(required = false) Integer month,
                            Model model) {
        YearMonth ym = (year != null && month != null)
            ? YearMonth.of(year, month) : YearMonth.now();
        CalendarMonth cm = new CalendarMonth(ym, LocalDate.now());

        Map<String, List<Schedule>> schedulesByDate = new LinkedHashMap<>();
        Map<String, List<Holiday>>  holidaysByDate  = new LinkedHashMap<>();

        // 일정 — 해당 master 의 인스턴스만
        List<Schedule> rows = service.findOverlapping(scheduleMasterId,
            cm.getGridStart().atStartOfDay(),
            cm.getGridEnd().atTime(LocalTime.MAX));
        for (Schedule sc : rows) {
            LocalDate from = sc.getStartAt().toLocalDate();
            LocalDate to   = sc.getEndAt().toLocalDate();
            if (from.isBefore(cm.getGridStart())) from = cm.getGridStart();
            if (to.isAfter(cm.getGridEnd()))      to   = cm.getGridEnd();
            LocalDate cur = from;
            while (!cur.isAfter(to)) {
                schedulesByDate.computeIfAbsent(cur.toString(),
                                                   k -> new java.util.ArrayList<>()).add(sc);
                cur = cur.plusDays(1);
            }
        }
        // 공휴일은 사이트와 무관하게 전역 — 항상 노출
        for (Holiday h : holidayService.findInRange(cm.getGridStart(), cm.getGridEnd())) {
            holidaysByDate.computeIfAbsent(h.getHolidayDate().toString(),
                                              k -> new java.util.ArrayList<>()).add(h);
        }

        model.addAttribute("cm", cm);
        model.addAttribute("schedulesByDate", schedulesByDate);
        model.addAttribute("holidaysByDate", holidaysByDate);
        return "front/schedule/calendar";
    }

    @GetMapping("/week")
    public String week(@PathVariable String scheduleMasterId,
                        @RequestParam(required = false) String date,
                        Model model) {
        LocalDate ref = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        CalendarWeek cw = new CalendarWeek(ref, LocalDate.now());

        Map<String, List<Schedule>> schedulesByDate = new LinkedHashMap<>();
        Map<String, List<Holiday>>  holidaysByDate  = new LinkedHashMap<>();

        List<Schedule> rows = service.findOverlapping(scheduleMasterId,
            cw.getGridStart().atStartOfDay(),
            cw.getGridEnd().atTime(LocalTime.MAX));
        for (Schedule sc : rows) {
            LocalDate from = sc.getStartAt().toLocalDate();
            LocalDate to   = sc.getEndAt().toLocalDate();
            if (from.isBefore(cw.getGridStart())) from = cw.getGridStart();
            if (to.isAfter(cw.getGridEnd()))      to   = cw.getGridEnd();
            LocalDate cur = from;
            while (!cur.isAfter(to)) {
                schedulesByDate.computeIfAbsent(cur.toString(),
                                                   k -> new java.util.ArrayList<>()).add(sc);
                cur = cur.plusDays(1);
            }
        }
        for (Holiday h : holidayService.findInRange(cw.getGridStart(), cw.getGridEnd())) {
            holidaysByDate.computeIfAbsent(h.getHolidayDate().toString(),
                                              k -> new java.util.ArrayList<>()).add(h);
        }

        model.addAttribute("cw", cw);
        model.addAttribute("schedulesByDate", schedulesByDate);
        model.addAttribute("holidaysByDate", holidaysByDate);
        return "front/schedule/week";
    }

    @GetMapping("/{scheduleId}")
    public String detail(@PathVariable String scheduleMasterId,
                          @PathVariable String scheduleId,
                          Model model) {
        Schedule sc = service.get(scheduleId);
        if (sc == null
            || !"Y".equals(sc.getUseYn())
            || !Objects.equals(scheduleMasterId, sc.getScheduleMasterId())) {
            model.addAttribute("error", "일정을 찾을 수 없거나 사용중지 상태입니다.");
            return "front/schedule/detail";
        }
        model.addAttribute("schedule", sc);
        return "front/schedule/detail";
    }
}
