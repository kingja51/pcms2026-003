package com.gonet.primary.survey.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.survey.dto.SurveyMaster;
import com.gonet.primary.survey.dto.SurveyMasterSaveForm;
import com.gonet.primary.survey.dto.SurveyMasterSearch;
import com.gonet.primary.survey.service.SurveyMasterService;
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
 * 설문 마스터(그룹) 관리 — site/menu 컨텍스트 + 그룹 헤더.
 *
 * <p>설문 detail (개별 설문 인스턴스) 은 {@link SurveyMngController} 가 담당.
 */
@Controller
@RequestMapping("/admin/system/survey-master")
public class SurveyMasterMngController {

    private static final Logger log = LoggerFactory.getLogger(SurveyMasterMngController.class);

    private final SurveyMasterService service;
    private final SiteService         siteService;

    public SurveyMasterMngController(SurveyMasterService service,
                                       SiteService siteService) {
        this.service     = service;
        this.siteService = siteService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") SurveyMasterSearch search, Model model) {
        search.setSortDir("DESC");
        List<SurveyMaster> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("sites", listSites());
        return "admin/system/survey-master/list";
    }

    @GetMapping("/{surveyMasterId}")
    public String detail(@PathVariable String surveyMasterId, Model model, RedirectAttributes ra) {
        SurveyMaster m = service.get(surveyMasterId);
        if (m == null) {
            ra.addFlashAttribute("error", "설문 마스터를 찾을 수 없습니다.");
            return "redirect:/admin/system/survey-master";
        }
        model.addAttribute("master", m);
        return "admin/system/survey-master/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            SurveyMasterSaveForm form = new SurveyMasterSaveForm();
            form.setUseYn("Y");
            model.addAttribute("form", form);
        }
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "create");
        return "admin/system/survey-master/form";
    }

    @GetMapping("/{surveyMasterId}/edit")
    public String editForm(@PathVariable String surveyMasterId,
                            Model model, RedirectAttributes ra) {
        SurveyMaster m = service.get(surveyMasterId);
        if (m == null) {
            ra.addFlashAttribute("error", "설문 마스터를 찾을 수 없습니다.");
            return "redirect:/admin/system/survey-master";
        }
        model.addAttribute("form", toForm(m));
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "edit");
        return "admin/system/survey-master/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") SurveyMasterSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/survey-master/new";
        }
        try {
            String id = service.create(form);
            ra.addFlashAttribute("message", "설문 마스터를 등록했습니다.");
            String target = "/admin/system/survey-master/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/survey-master/new";
        } catch (Exception ex) {
            log.warn("SURVEY_MASTER_CREATE error", ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/survey-master/new";
        }
    }

    @PostMapping("/{surveyMasterId}")
    public String update(@PathVariable String surveyMasterId,
                          @Valid @ModelAttribute("form") SurveyMasterSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setSurveyMasterId(surveyMasterId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/survey-master/" + surveyMasterId + "/edit";
        }
        try {
            service.update(form);
            ra.addFlashAttribute("message", "설문 마스터를 수정했습니다.");
            String target = "/admin/system/survey-master/" + surveyMasterId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/survey-master/" + surveyMasterId + "/edit";
        } catch (Exception ex) {
            log.warn("SURVEY_MASTER_UPDATE error id={}", surveyMasterId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/survey-master/" + surveyMasterId + "/edit";
        }
    }

    @PostMapping("/{surveyMasterId}/use")
    public String toggleUse(@PathVariable String surveyMasterId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/survey-master/" + surveyMasterId;
        try {
            service.toggleUse(surveyMasterId, active);
            ra.addFlashAttribute("message", active ? "사용 처리했습니다." : "사용중지했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("SURVEY_MASTER_USE_TOGGLE error", ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{surveyMasterId}/delete")
    public String delete(@PathVariable String surveyMasterId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(surveyMasterId);
            ra.addFlashAttribute("message", "설문 마스터를 삭제했습니다. 연결된 설문도 함께 삭제됩니다.");
            String target = "/admin/system/survey-master";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (Exception ex) {
            log.warn("SURVEY_MASTER_DELETE error", ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/survey-master/" + surveyMasterId;
        }
    }

    private List<Site> listSites() {
        SiteSearch ss = new SiteSearch();
        ss.setPage(1);
        ss.setPageSizeUnbounded(500);
        return siteService.search(ss);
    }

    private SurveyMasterSaveForm toForm(SurveyMaster m) {
        SurveyMasterSaveForm f = new SurveyMasterSaveForm();
        f.setSurveyMasterId(m.getSurveyMasterId());
        f.setSiteId(m.getSiteId());
        f.setMenuId(m.getMenuId());
        f.setMasterTitle(m.getMasterTitle());
        f.setMasterContent(m.getMasterContent());
        f.setUseYn(m.getUseYn());
        return f;
    }
}
