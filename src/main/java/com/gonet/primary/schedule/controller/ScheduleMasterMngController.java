package com.gonet.primary.schedule.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.schedule.dto.ScheduleMaster;
import com.gonet.primary.schedule.dto.ScheduleMasterSaveForm;
import com.gonet.primary.schedule.dto.ScheduleMasterSearch;
import com.gonet.primary.schedule.service.ScheduleMasterService;
import com.gonet.primary.system.site.dto.Site;
import com.gonet.primary.system.site.dto.SiteSearch;
import com.gonet.primary.system.site.service.SiteService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 일정 마스터(그룹) 관리 — site/menu 컨텍스트 + 그룹 헤더.
 *
 * <p>일정 detail (개별 일정) 은 {@link ScheduleMngController} 가 담당.
 */
@Controller
@RequestMapping("/admin/system/schedule-master")
public class ScheduleMasterMngController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleMasterMngController.class);

    private final ScheduleMasterService service;
    private final SiteService           siteService;

    public ScheduleMasterMngController(ScheduleMasterService service,
                                         SiteService siteService) {
        this.service     = service;
        this.siteService = siteService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") ScheduleMasterSearch search, Model model) {
        search.setSortDir("DESC");
        List<ScheduleMaster> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("sites", listSites());
        return "admin/system/schedule-master/list";
    }

    @GetMapping("/{scheduleMasterId}")
    public String detail(@PathVariable String scheduleMasterId, Model model, RedirectAttributes ra) {
        ScheduleMaster m = service.get(scheduleMasterId);
        if (m == null) {
            ra.addFlashAttribute("error", "일정 마스터를 찾을 수 없습니다.");
            return "redirect:/admin/system/schedule-master";
        }
        model.addAttribute("master", m);
        return "admin/system/schedule-master/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            ScheduleMasterSaveForm form = new ScheduleMasterSaveForm();
            form.setUseYn("Y");
            model.addAttribute("form", form);
        }
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "create");
        return "admin/system/schedule-master/form";
    }

    @GetMapping("/{scheduleMasterId}/edit")
    public String editForm(@PathVariable String scheduleMasterId,
                            Model model, RedirectAttributes ra) {
        ScheduleMaster m = service.get(scheduleMasterId);
        if (m == null) {
            ra.addFlashAttribute("error", "일정 마스터를 찾을 수 없습니다.");
            return "redirect:/admin/system/schedule-master";
        }
        model.addAttribute("form", toForm(m));
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "edit");
        return "admin/system/schedule-master/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ScheduleMasterSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/schedule-master/new";
        }
        try {
            String id = service.create(form);
            ra.addFlashAttribute("message", "일정 마스터를 등록했습니다.");
            String target = "/admin/system/schedule-master/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/schedule-master/new";
        } catch (Exception ex) {
            log.warn("SCHEDULE_MASTER_CREATE error", ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/schedule-master/new";
        }
    }

    @PostMapping("/{scheduleMasterId}")
    public String update(@PathVariable String scheduleMasterId,
                          @Valid @ModelAttribute("form") ScheduleMasterSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setScheduleMasterId(scheduleMasterId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/schedule-master/" + scheduleMasterId + "/edit";
        }
        try {
            service.update(form);
            ra.addFlashAttribute("message", "일정 마스터를 수정했습니다.");
            String target = "/admin/system/schedule-master/" + scheduleMasterId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/schedule-master/" + scheduleMasterId + "/edit";
        } catch (Exception ex) {
            log.warn("SCHEDULE_MASTER_UPDATE error id={}", scheduleMasterId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/schedule-master/" + scheduleMasterId + "/edit";
        }
    }

    @PostMapping("/{scheduleMasterId}/use")
    public String toggleUse(@PathVariable String scheduleMasterId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/schedule-master/" + scheduleMasterId;
        try {
            service.toggleUse(scheduleMasterId, active);
            ra.addFlashAttribute("message", active ? "사용 처리했습니다." : "사용중지했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("SCHEDULE_MASTER_USE_TOGGLE error", ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{scheduleMasterId}/delete")
    public String delete(@PathVariable String scheduleMasterId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(scheduleMasterId);
            ra.addFlashAttribute("message", "일정 마스터를 삭제했습니다. 연결된 일정도 함께 삭제됩니다.");
            String target = "/admin/system/schedule-master";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (Exception ex) {
            log.warn("SCHEDULE_MASTER_DELETE error", ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/schedule-master/" + scheduleMasterId;
        }
    }

    private List<Site> listSites() {
        SiteSearch ss = new SiteSearch();
        ss.setPage(1);
        ss.setPageSizeUnbounded(500);
        return siteService.search(ss);
    }

    private ScheduleMasterSaveForm toForm(ScheduleMaster m) {
        ScheduleMasterSaveForm f = new ScheduleMasterSaveForm();
        f.setScheduleMasterId(m.getScheduleMasterId());
        f.setSiteId(m.getSiteId());
        f.setMenuId(m.getMenuId());
        f.setMasterTitle(m.getMasterTitle());
        f.setMasterContent(m.getMasterContent());
        f.setUseYn(m.getUseYn());
        return f;
    }
}
