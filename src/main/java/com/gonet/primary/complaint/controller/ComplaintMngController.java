package com.gonet.primary.complaint.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.complaint.dto.*;
import com.gonet.primary.complaint.service.*;
import com.gonet.primary.system.site.dto.SiteSearch;
import com.gonet.primary.system.site.service.SiteService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 민원 관리 통합 Controller — Master CRUD + Category + Article 모더레이션 + Answer.
 *
 * <ul>
 *   <li>Master   : {@code /admin/system/complaint}</li>
 *   <li>Category : {@code /admin/system/complaint/{masterId}/category}</li>
 *   <li>Article  : {@code /admin/system/complaint/{masterId}/article}</li>
 *   <li>Answer   : {@code /admin/system/complaint/{masterId}/article/{articleId}/answer}</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/system/complaint")
public class ComplaintMngController {

    private static final Logger log = LoggerFactory.getLogger(ComplaintMngController.class);

    private final ComplaintMasterService   masterService;
    private final ComplaintCategoryService categoryService;
    private final ComplaintArticleService  articleService;
    private final ComplaintAnswerService   answerService;
    private final SiteService              siteService;

    public ComplaintMngController(ComplaintMasterService masterService,
                                  ComplaintCategoryService categoryService,
                                  ComplaintArticleService articleService,
                                  ComplaintAnswerService answerService,
                                  SiteService siteService) {
        this.masterService   = masterService;
        this.categoryService = categoryService;
        this.articleService  = articleService;
        this.answerService   = answerService;
        this.siteService     = siteService;
    }

    // ══════════════════════════════════════════════════════════════════
    // ❶ Master — 민원 마스터 CRUD
    // ══════════════════════════════════════════════════════════════════

    @GetMapping
    public String masterList(@ModelAttribute("search") ComplaintMasterSearch search, Model model) {
        PageResponse<ComplaintMaster> page = masterService.list(search);
        model.addAttribute("page", page);
        model.addAttribute("sites", listSites());
        return "admin/system/complaint/list";
    }

    @GetMapping("/new")
    public String masterCreateForm(Model model) {
        if (!model.containsAttribute("form")) {
            ComplaintMasterSaveForm form = new ComplaintMasterSaveForm();
            model.addAttribute("form", form);
        }
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "create");
        return "admin/system/complaint/form";
    }

    @GetMapping("/{masterId}")
    public String masterDetail(@PathVariable String masterId, Model model, RedirectAttributes ra) {
        ComplaintMaster master = masterService.get(masterId);
        if (master == null) {
            ra.addFlashAttribute("error", "민원 마스터를 찾을 수 없습니다.");
            return "redirect:/admin/system/complaint";
        }
        model.addAttribute("master", master);
        model.addAttribute("categories", categoryService.listByMaster(masterId));
        return "admin/system/complaint/detail";
    }

    @GetMapping("/{masterId}/edit")
    public String masterEditForm(@PathVariable String masterId, Model model, RedirectAttributes ra) {
        ComplaintMaster master = masterService.get(masterId);
        if (master == null) {
            ra.addFlashAttribute("error", "민원 마스터를 찾을 수 없습니다.");
            return "redirect:/admin/system/complaint";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", toMasterForm(master));
        }
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "edit");
        return "admin/system/complaint/form";
    }

    @PostMapping
    public String masterCreate(@Valid @ModelAttribute("form") ComplaintMasterSaveForm form,
                               BindingResult br, RedirectAttributes ra,
                               HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/complaint/new";
        }
        try {
            String id = masterService.create(form);
            log.info("COMPLAINT_MASTER_CREATE ok id={} code={}", id, form.getMasterCode());
            ra.addFlashAttribute("message", "민원 마스터를 등록했습니다.");
            String target = "/admin/system/complaint/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("COMPLAINT_MASTER_CREATE fail reason={}", ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/complaint/new";
        } catch (Exception ex) {
            log.warn("COMPLAINT_MASTER_CREATE error", ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/complaint/new";
        }
    }

    @PostMapping("/{masterId}")
    public String masterUpdate(@PathVariable String masterId,
                               @Valid @ModelAttribute("form") ComplaintMasterSaveForm form,
                               BindingResult br, RedirectAttributes ra,
                               HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/complaint/" + masterId + "/edit";
        }
        try {
            masterService.update(masterId, form);
            log.info("COMPLAINT_MASTER_UPDATE ok id={}", masterId);
            ra.addFlashAttribute("message", "민원 마스터를 수정했습니다.");
            String target = "/admin/system/complaint/" + masterId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("COMPLAINT_MASTER_UPDATE fail id={} reason={}", masterId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/complaint/" + masterId + "/edit";
        } catch (Exception ex) {
            log.warn("COMPLAINT_MASTER_UPDATE error id={}", masterId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/complaint/" + masterId + "/edit";
        }
    }

    @PostMapping("/{masterId}/use")
    public String masterToggleUse(@PathVariable String masterId,
                                  @RequestParam("active") boolean active,
                                  RedirectAttributes ra, HttpServletResponse res) {
        String redirect = "/admin/system/complaint/" + masterId;
        try {
            masterService.toggleUse(masterId, active);
            ra.addFlashAttribute("message", active ? "사용 처리했습니다." : "사용중지 처리했습니다.");
            res.setHeader("HX-Redirect", redirect);
        } catch (Exception ex) {
            log.warn("COMPLAINT_MASTER_USE_TOGGLE error id={}", masterId, ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
        }
        return "redirect:" + redirect;
    }

    @PostMapping("/{masterId}/delete")
    public String masterDelete(@PathVariable String masterId,
                               RedirectAttributes ra, HttpServletResponse res) {
        try {
            masterService.delete(masterId);
            log.info("COMPLAINT_MASTER_DELETE ok id={}", masterId);
            ra.addFlashAttribute("message", "민원 마스터를 삭제했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/complaint");
            return "redirect:/admin/system/complaint";
        } catch (Exception ex) {
            log.warn("COMPLAINT_MASTER_DELETE error id={}", masterId, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/complaint/" + masterId;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ❷ Category — 카테고리 CRUD (마스터 하위)
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/{masterId}/category/new")
    public String categoryCreateForm(@PathVariable String masterId, Model model, RedirectAttributes ra) {
        ComplaintMaster master = masterService.get(masterId);
        if (master == null) {
            ra.addFlashAttribute("error", "민원 마스터를 찾을 수 없습니다.");
            return "redirect:/admin/system/complaint";
        }
        if (!model.containsAttribute("form")) {
            ComplaintCategorySaveForm form = new ComplaintCategorySaveForm();
            form.setComplaintMasterId(masterId);
            form.setUseYn("Y");
            model.addAttribute("form", form);
        }
        model.addAttribute("master", master);
        model.addAttribute("mode", "create");
        return "admin/system/complaint/category/form";
    }

    @GetMapping("/{masterId}/category/{categoryId}/edit")
    public String categoryEditForm(@PathVariable String masterId,
                                   @PathVariable String categoryId,
                                   Model model, RedirectAttributes ra) {
        ComplaintCategory cat = categoryService.get(categoryId);
        if (cat == null) {
            ra.addFlashAttribute("error", "카테고리를 찾을 수 없습니다.");
            return "redirect:/admin/system/complaint/" + masterId;
        }
        if (!model.containsAttribute("form")) {
            ComplaintCategorySaveForm form = new ComplaintCategorySaveForm();
            form.setComplaintMasterId(masterId);
            form.setCategoryCode(cat.getCategoryCode());
            form.setCategoryName(cat.getCategoryName());
            form.setDescription(cat.getDescription());
            form.setSortOrder(cat.getSortOrder());
            form.setUseYn(cat.getUseYn());
            model.addAttribute("form", form);
        }
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("master", masterService.get(masterId));
        model.addAttribute("mode", "edit");
        return "admin/system/complaint/category/form";
    }

    @PostMapping("/{masterId}/category")
    public String categoryCreate(@PathVariable String masterId,
                                 @Valid @ModelAttribute("form") ComplaintCategorySaveForm form,
                                 BindingResult br, RedirectAttributes ra) {
        form.setComplaintMasterId(masterId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/complaint/" + masterId + "/category/new";
        }
        try {
            categoryService.create(form);
            ra.addFlashAttribute("message", "카테고리를 등록했습니다.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/complaint/" + masterId + "/category/new";
        } catch (Exception ex) {
            log.warn("COMPLAINT_CATEGORY_CREATE error masterId={}", masterId, ex);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/complaint/" + masterId + "/category/new";
        }
        return "redirect:/admin/system/complaint/" + masterId;
    }

    @PostMapping("/{masterId}/category/{categoryId}")
    public String categoryUpdate(@PathVariable String masterId,
                                 @PathVariable String categoryId,
                                 @Valid @ModelAttribute("form") ComplaintCategorySaveForm form,
                                 BindingResult br, RedirectAttributes ra) {
        form.setComplaintMasterId(masterId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/complaint/" + masterId + "/category/" + categoryId + "/edit";
        }
        try {
            categoryService.update(categoryId, form);
            ra.addFlashAttribute("message", "카테고리를 수정했습니다.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/complaint/" + masterId + "/category/" + categoryId + "/edit";
        } catch (Exception ex) {
            log.warn("COMPLAINT_CATEGORY_UPDATE error id={}", categoryId, ex);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/complaint/" + masterId + "/category/" + categoryId + "/edit";
        }
        return "redirect:/admin/system/complaint/" + masterId;
    }

    @PostMapping("/{masterId}/category/{categoryId}/use")
    public String categoryToggleUse(@PathVariable String masterId,
                                    @PathVariable String categoryId,
                                    @RequestParam("active") boolean active,
                                    RedirectAttributes ra) {
        try {
            categoryService.toggleUse(categoryId, active);
            ra.addFlashAttribute("message", active ? "사용 처리했습니다." : "사용중지 처리했습니다.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/system/complaint/" + masterId;
    }

    @PostMapping("/{masterId}/category/{categoryId}/delete")
    public String categoryDelete(@PathVariable String masterId,
                                 @PathVariable String categoryId,
                                 RedirectAttributes ra) {
        try {
            categoryService.delete(categoryId);
            ra.addFlashAttribute("message", "카테고리를 삭제했습니다.");
        } catch (Exception ex) {
            log.warn("COMPLAINT_CATEGORY_DELETE error id={}", categoryId, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/system/complaint/" + masterId;
    }

    // ══════════════════════════════════════════════════════════════════
    // ❸ Article — 접수 민원 목록/상세/상태 변경
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/{masterId}/article")
    public String articleList(@PathVariable String masterId,
                              @ModelAttribute("search") ComplaintArticleSearch search,
                              Model model, RedirectAttributes ra) {
        ComplaintMaster master = masterService.get(masterId);
        if (master == null) {
            ra.addFlashAttribute("error", "민원 마스터를 찾을 수 없습니다.");
            return "redirect:/admin/system/complaint";
        }
        search.setComplaintMasterId(masterId);
        PageResponse<ComplaintArticle> page = articleService.list(search);
        model.addAttribute("page", page);
        model.addAttribute("master", master);
        model.addAttribute("categories", categoryService.listByMaster(masterId));
        model.addAttribute("statuses", ComplaintStatus.values());
        return "admin/system/complaint/article/list";
    }

    @GetMapping("/{masterId}/article/{articleId}")
    public String articleDetail(@PathVariable String masterId,
                                @PathVariable String articleId,
                                Model model, RedirectAttributes ra) {
        ComplaintMaster master = masterService.get(masterId);
        ComplaintArticle article = articleService.get(articleId);
        if (master == null || article == null) {
            ra.addFlashAttribute("error", "민원을 찾을 수 없습니다.");
            return "redirect:/admin/system/complaint/" + masterId + "/article";
        }
        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("answers", answerService.listByArticle(articleId));
        model.addAttribute("statuses", ComplaintStatus.values());
        model.addAttribute("answerForm", new ComplaintAnswerSaveForm());
        return "admin/system/complaint/article/detail";
    }

    @PostMapping("/{masterId}/article/{articleId}/status")
    public String articleUpdateStatus(@PathVariable String masterId,
                                      @PathVariable String articleId,
                                      @RequestParam("status") String status,
                                      RedirectAttributes ra, HttpServletResponse res) {
        String redirect = "/admin/system/complaint/" + masterId + "/article/" + articleId;
        try {
            articleService.updateStatus(articleId, status);
            ra.addFlashAttribute("message", "상태를 변경했습니다.");
            res.setHeader("HX-Redirect", redirect);
        } catch (Exception ex) {
            log.warn("COMPLAINT_STATUS_CHANGE error id={}", articleId, ex);
            ra.addFlashAttribute("error", "상태 변경 중 오류가 발생했습니다.");
        }
        return "redirect:" + redirect;
    }

    @PostMapping("/{masterId}/article/{articleId}/delete")
    public String articleDelete(@PathVariable String masterId,
                                @PathVariable String articleId,
                                RedirectAttributes ra, HttpServletResponse res) {
        try {
            articleService.delete(articleId);
            log.info("COMPLAINT_DELETE ok id={}", articleId);
            ra.addFlashAttribute("message", "민원을 삭제했습니다.");
            String target = "/admin/system/complaint/" + masterId + "/article";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (Exception ex) {
            log.warn("COMPLAINT_DELETE error id={}", articleId, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/complaint/" + masterId + "/article/" + articleId;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ❹ Answer — 답변 등록/수정/삭제
    // ══════════════════════════════════════════════════════════════════

    @PostMapping("/{masterId}/article/{articleId}/answer")
    public String answerCreate(@PathVariable String masterId,
                               @PathVariable String articleId,
                               @Valid @ModelAttribute("answerForm") ComplaintAnswerSaveForm form,
                               BindingResult br, RedirectAttributes ra,
                               Authentication auth) {
        form.setArticleId(articleId);
        String redirect = "/admin/system/complaint/" + masterId + "/article/" + articleId;
        if (br.hasErrors()) {
            ra.addFlashAttribute("answerForm", form);
            ra.addFlashAttribute("answerErrors", br.getAllErrors());
            return "redirect:" + redirect;
        }
        try {
            answerService.create(form, auth);
            ra.addFlashAttribute("message", "답변을 등록했습니다.");
        } catch (Exception ex) {
            log.warn("COMPLAINT_ANSWER_CREATE error articleId={}", articleId, ex);
            ra.addFlashAttribute("error", "답변 등록 중 오류가 발생했습니다.");
        }
        return "redirect:" + redirect;
    }

    @PostMapping("/{masterId}/article/{articleId}/answer/{answerId}")
    public String answerUpdate(@PathVariable String masterId,
                               @PathVariable String articleId,
                               @PathVariable String answerId,
                               @Valid @ModelAttribute("answerForm") ComplaintAnswerSaveForm form,
                               BindingResult br, RedirectAttributes ra) {
        form.setArticleId(articleId);
        String redirect = "/admin/system/complaint/" + masterId + "/article/" + articleId;
        if (br.hasErrors()) {
            ra.addFlashAttribute("answerForm", form);
            ra.addFlashAttribute("answerErrors", br.getAllErrors());
            return "redirect:" + redirect;
        }
        try {
            answerService.update(answerId, form);
            ra.addFlashAttribute("message", "답변을 수정했습니다.");
        } catch (Exception ex) {
            log.warn("COMPLAINT_ANSWER_UPDATE error id={}", answerId, ex);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
        }
        return "redirect:" + redirect;
    }

    @PostMapping("/{masterId}/article/{articleId}/answer/{answerId}/delete")
    public String answerDelete(@PathVariable String masterId,
                               @PathVariable String articleId,
                               @PathVariable String answerId,
                               RedirectAttributes ra) {
        String redirect = "/admin/system/complaint/" + masterId + "/article/" + articleId;
        try {
            answerService.delete(answerId);
            ra.addFlashAttribute("message", "답변을 삭제했습니다.");
        } catch (Exception ex) {
            log.warn("COMPLAINT_ANSWER_DELETE error id={}", answerId, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
        }
        return "redirect:" + redirect;
    }

    // ══════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════

    private java.util.List<com.gonet.primary.system.site.dto.Site> listSites() {
        SiteSearch ss = new SiteSearch();
        ss.setPage(1);
        ss.setPageSizeUnbounded(500);
        return siteService.search(ss);
    }

    private ComplaintMasterSaveForm toMasterForm(ComplaintMaster m) {
        ComplaintMasterSaveForm f = new ComplaintMasterSaveForm();
        f.setComplaintMasterId(m.getComplaintMasterId());
        f.setSiteId(m.getSiteId());
        f.setMasterCode(m.getMasterCode());
        f.setMasterName(m.getMasterName());
        f.setDescription(m.getDescription());
        f.setMenuId(m.getMenuId());
        f.setNoticeHtml(m.getNoticeHtml());
        f.setAllowMemberYn(m.getAllowMemberYn());
        f.setAllowOauth2Yn(m.getAllowOauth2Yn());
        f.setAllowNiceYn(m.getAllowNiceYn());
        f.setShowInList(m.getShowInList());
        f.setFileYn(m.getFileYn());
        f.setFileCountMax(m.getFileCountMax() != null ? m.getFileCountMax() : 5);
        f.setFileSizeMb(m.getFileSizeMax() != null ? (int)(m.getFileSizeMax() / 1024 / 1024) : 10);
        f.setAnswerRequiredYn(m.getAnswerRequiredYn());
        f.setAnswerDeadlineDays(m.getAnswerDeadlineDays());
        f.setUseYn(m.getUseYn());
        return f;
    }
}
