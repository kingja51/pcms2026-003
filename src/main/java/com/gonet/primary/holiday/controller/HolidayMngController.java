package com.gonet.primary.holiday.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.holiday.dto.Holiday;
import com.gonet.primary.holiday.dto.HolidaySaveForm;
import com.gonet.primary.holiday.dto.HolidaySearch;
import com.gonet.primary.holiday.service.HolidayService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/system/holiday")
public class HolidayMngController {

    private static final Logger log = LoggerFactory.getLogger(HolidayMngController.class);

    private final HolidayService service;

    public HolidayMngController(HolidayService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@ModelAttribute("search") HolidaySearch search, Model model) {
        search.setSortDir("DESC"); // 관리자 — 최신 공휴일부터
        List<Holiday> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/holiday/list";
    }

    @GetMapping("/{holidayId}")
    public String detail(@PathVariable String holidayId, Model model, RedirectAttributes ra) {
        Holiday h = service.get(holidayId);
        if (h == null) {
            ra.addFlashAttribute("error", "공휴일을 찾을 수 없습니다.");
            return "redirect:/admin/system/holiday";
        }
        model.addAttribute("holiday", h);
        return "admin/system/holiday/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            HolidaySaveForm form = new HolidaySaveForm();
            form.setHolidayType("PUBLIC");
            form.setUseYn("Y");
            model.addAttribute("form", form);
        }
        model.addAttribute("mode", "create");
        return "admin/system/holiday/form";
    }

    @GetMapping("/{holidayId}/edit")
    public String editForm(@PathVariable String holidayId, Model model, RedirectAttributes ra) {
        Holiday h = service.get(holidayId);
        if (h == null) {
            ra.addFlashAttribute("error", "공휴일을 찾을 수 없습니다.");
            return "redirect:/admin/system/holiday";
        }
        model.addAttribute("form", toForm(h));
        model.addAttribute("mode", "edit");
        return "admin/system/holiday/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") HolidaySaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/holiday/new";
        }
        try {
            String id = service.create(form);
            ra.addFlashAttribute("message", "공휴일을 등록했습니다.");
            String target = "/admin/system/holiday/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/holiday/new";
        } catch (Exception ex) {
            log.warn("HOLIDAY_CREATE error", ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/holiday/new";
        }
    }

    @PostMapping("/{holidayId}")
    public String update(@PathVariable String holidayId,
                          @Valid @ModelAttribute("form") HolidaySaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setHolidayId(holidayId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/holiday/" + holidayId + "/edit";
        }
        try {
            service.update(form);
            ra.addFlashAttribute("message", "공휴일을 수정했습니다.");
            String target = "/admin/system/holiday/" + holidayId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/holiday/" + holidayId + "/edit";
        } catch (Exception ex) {
            log.warn("HOLIDAY_UPDATE error id={}", holidayId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/holiday/" + holidayId + "/edit";
        }
    }

    @PostMapping("/{holidayId}/use")
    public String toggleUse(@PathVariable String holidayId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/holiday/" + holidayId;
        try {
            service.toggleUse(holidayId, active);
            ra.addFlashAttribute("message", active ? "사용 처리했습니다." : "사용중지했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("HOLIDAY_USE_TOGGLE error", ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{holidayId}/delete")
    public String delete(@PathVariable String holidayId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(holidayId);
            ra.addFlashAttribute("message", "공휴일을 삭제했습니다.");
            String target = "/admin/system/holiday";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (Exception ex) {
            log.warn("HOLIDAY_DELETE error", ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/holiday/" + holidayId;
        }
    }

    private HolidaySaveForm toForm(Holiday h) {
        HolidaySaveForm f = new HolidaySaveForm();
        f.setHolidayId(h.getHolidayId());
        f.setHolidayDate(h.getHolidayDate());
        f.setHolidayName(h.getHolidayName());
        f.setHolidayType(h.getHolidayType());
        f.setDescription(h.getDescription());
        f.setUseYn(h.getUseYn());
        return f;
    }
}
