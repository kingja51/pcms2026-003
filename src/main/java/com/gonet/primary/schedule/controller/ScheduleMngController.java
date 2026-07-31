package com.gonet.primary.schedule.controller;

import com.gonet.common.calendar.CalendarMonth;
import com.gonet.common.calendar.CalendarWeek;
import com.gonet.common.dto.PageResponse;
import com.gonet.primary.holiday.dto.Holiday;
import com.gonet.primary.holiday.service.HolidayService;
import com.gonet.primary.schedule.dto.Schedule;
import com.gonet.primary.schedule.dto.ScheduleMaster;
import com.gonet.primary.schedule.dto.ScheduleMasterSearch;
import com.gonet.primary.schedule.dto.ScheduleSaveForm;
import com.gonet.primary.schedule.dto.ScheduleSearch;
import com.gonet.primary.schedule.service.ScheduleMasterService;
import com.gonet.primary.schedule.service.ScheduleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 일정(detail) 관리. site 컨텍스트는 master 가 보유 — 본 컨트롤러는 scheduleMasterId 로 스코프.
 */
@Controller
@RequestMapping("/admin/system/schedule")
public class ScheduleMngController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleMngController.class);

    private final ScheduleService       service;
    private final ScheduleMasterService  masterService;
    private final HolidayService         holidayService;

    public ScheduleMngController(ScheduleService service,
                                  ScheduleMasterService masterService,
                                  HolidayService holidayService) {
        this.service        = service;
        this.masterService  = masterService;
        this.holidayService = holidayService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") ScheduleSearch search, Model model) {
        search.setSortDir("DESC"); // 관리자 — 신규 일정이 위로
        List<Schedule> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("masters", listMasters());
        return "admin/system/schedule/list";
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) Integer year,
                            @RequestParam(required = false) Integer month,
                            @RequestParam(required = false) String scheduleMasterId,
                            Model model) {
        YearMonth ym = (year != null && month != null)
            ? YearMonth.of(year, month) : YearMonth.now();
        CalendarMonth cm = new CalendarMonth(ym, LocalDate.now());

        Map<String, List<Schedule>> schedulesByDate = new LinkedHashMap<>();
        Map<String, List<Holiday>>  holidaysByDate  = new LinkedHashMap<>();

        // 일정 — scheduleMasterId 비어있으면 전체 마스터
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
        for (Holiday h : holidayService.findInRange(cm.getGridStart(), cm.getGridEnd())) {
            holidaysByDate.computeIfAbsent(h.getHolidayDate().toString(),
                                              k -> new java.util.ArrayList<>()).add(h);
        }

        model.addAttribute("cm", cm);
        model.addAttribute("schedulesByDate", schedulesByDate);
        model.addAttribute("holidaysByDate", holidaysByDate);
        model.addAttribute("masters", listMasters());
        model.addAttribute("scheduleMasterId", scheduleMasterId);
        return "admin/system/schedule/calendar";
    }

    @GetMapping("/week")
    public String week(@RequestParam(required = false) String date,
                        @RequestParam(required = false) String scheduleMasterId,
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
        model.addAttribute("masters", listMasters());
        model.addAttribute("scheduleMasterId", scheduleMasterId);
        return "admin/system/schedule/week";
    }

    @GetMapping("/{scheduleId}")
    public String detail(@PathVariable String scheduleId, Model model, RedirectAttributes ra) {
        Schedule sc = service.get(scheduleId);
        if (sc == null) {
            ra.addFlashAttribute("error", "일정을 찾을 수 없습니다.");
            return "redirect:/admin/system/schedule";
        }
        model.addAttribute("schedule", sc);
        return "admin/system/schedule/detail";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) String scheduleMasterId, Model model) {
        if (!model.containsAttribute("form")) {
            ScheduleSaveForm form = new ScheduleSaveForm();
            form.setUseYn("Y");
            form.setAllDayYn("N");
            form.setScheduleMasterId(scheduleMasterId);
            model.addAttribute("form", form);
        }
        model.addAttribute("masters", listMasters());
        model.addAttribute("mode", "create");
        return "admin/system/schedule/form";
    }

    @GetMapping("/{scheduleId}/edit")
    public String editForm(@PathVariable String scheduleId, Model model, RedirectAttributes ra) {
        Schedule sc = service.get(scheduleId);
        if (sc == null) {
            ra.addFlashAttribute("error", "일정을 찾을 수 없습니다.");
            return "redirect:/admin/system/schedule";
        }
        model.addAttribute("form", toForm(sc));
        model.addAttribute("masters", listMasters());
        model.addAttribute("mode", "edit");
        return "admin/system/schedule/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ScheduleSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/schedule/new";
        }
        try {
            String id = service.create(form);
            ra.addFlashAttribute("message", "일정을 등록했습니다.");
            String target = "/admin/system/schedule/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/schedule/new";
        } catch (Exception ex) {
            log.warn("SCHEDULE_CREATE error", ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/schedule/new";
        }
    }

    @PostMapping("/{scheduleId}")
    public String update(@PathVariable String scheduleId,
                          @Valid @ModelAttribute("form") ScheduleSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setScheduleId(scheduleId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/schedule/" + scheduleId + "/edit";
        }
        try {
            service.update(form);
            ra.addFlashAttribute("message", "일정을 수정했습니다.");
            String target = "/admin/system/schedule/" + scheduleId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/schedule/" + scheduleId + "/edit";
        } catch (Exception ex) {
            log.warn("SCHEDULE_UPDATE error id={}", scheduleId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/schedule/" + scheduleId + "/edit";
        }
    }

    @PostMapping("/{scheduleId}/use")
    public String toggleUse(@PathVariable String scheduleId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/schedule/" + scheduleId;
        try {
            service.toggleUse(scheduleId, active);
            ra.addFlashAttribute("message", active ? "사용 처리했습니다." : "사용중지했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("SCHEDULE_USE_TOGGLE error", ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{scheduleId}/delete")
    public String delete(@PathVariable String scheduleId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(scheduleId);
            ra.addFlashAttribute("message", "일정을 삭제했습니다.");
            String target = "/admin/system/schedule";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (Exception ex) {
            log.warn("SCHEDULE_DELETE error", ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/schedule/" + scheduleId;
        }
    }

    private List<ScheduleMaster> listMasters() {
        ScheduleMasterSearch s = new ScheduleMasterSearch();
        s.setPage(1);
        s.setPageSizeUnbounded(500);
        s.setUseYn("Y");
        return masterService.search(s);
    }

    private ScheduleSaveForm toForm(Schedule sc) {
        ScheduleSaveForm f = new ScheduleSaveForm();
        f.setScheduleId(sc.getScheduleId());
        f.setScheduleMasterId(sc.getScheduleMasterId());
        f.setScheduleTitle(sc.getScheduleTitle());
        f.setScheduleContent(sc.getScheduleContent());
        f.setScheduleCategory(sc.getScheduleCategory());
        f.setStartAt(sc.getStartAt());
        f.setEndAt(sc.getEndAt());
        f.setLocation(sc.getLocation());
        f.setLinkUrl(sc.getLinkUrl());
        f.setAllDayYn(sc.getAllDayYn());
        f.setColorCode(sc.getColorCode());
        f.setUseYn(sc.getUseYn());
        return f;
    }
}
