package com.gonet.primary.board.category.controller;

import com.gonet.primary.board.category.dto.BbsCategory;
import com.gonet.primary.board.category.dto.BbsCategorySaveForm;
import com.gonet.primary.board.category.service.BoardCategoryService;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.service.BoardMasterService;
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
 * 게시판 카테고리 관리자 화면 — 게시판마스터 detail 하위.
 *
 * <p>URL : {@code /admin/system/board/{bbsMasterId}/category/**}
 */
@Controller
@RequestMapping("/admin/system/board/{bbsMasterId}/category")
public class BoardCategoryMngController {

    private static final Logger log = LoggerFactory.getLogger(BoardCategoryMngController.class);

    private final BoardCategoryService categoryService;
    private final BoardMasterService   masterService;

    public BoardCategoryMngController(BoardCategoryService categoryService,
                                       BoardMasterService masterService) {
        this.categoryService = categoryService;
        this.masterService   = masterService;
    }

    @ModelAttribute("master")
    public BbsMaster master(@PathVariable String bbsMasterId) {
        BbsMaster m = masterService.get(bbsMasterId);
        if (m == null) {
            throw new IllegalArgumentException("게시판을 찾을 수 없습니다: " + bbsMasterId);
        }
        return m;
    }

    @GetMapping
    public String list(@PathVariable String bbsMasterId, Model model) {
        List<BbsCategory> categories = categoryService.listAll(bbsMasterId);
        model.addAttribute("categories", categories);
        return "admin/system/board/category/list";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable String bbsMasterId, Model model) {
        BbsCategorySaveForm form = new BbsCategorySaveForm();
        form.setBbsMasterId(bbsMasterId);
        model.addAttribute("form", form);
        model.addAttribute("mode", "new");
        return "admin/system/board/category/form";
    }

    @PostMapping("/new")
    public String create(@PathVariable String bbsMasterId,
                          @Valid @ModelAttribute("form") BbsCategorySaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          Model model) {
        // 경로 변수와 form 의 bbsMasterId 일치 강제
        form.setBbsMasterId(bbsMasterId);
        if (br.hasErrors()) {
            model.addAttribute("mode", "new");
            return "admin/system/board/category/form";
        }
        try {
            categoryService.create(form);
            ra.addFlashAttribute("message", "카테고리를 등록했습니다.");
            return "redirect:/admin/system/board/" + bbsMasterId + "/category";
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_CATEGORY_CREATE_FAIL reason={}", ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            ra.addFlashAttribute("form", form);
            return "redirect:/admin/system/board/" + bbsMasterId + "/category/new";
        } catch (Exception ex) {
            log.warn("BBS_CATEGORY_CREATE_ERROR", ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:/admin/system/board/" + bbsMasterId + "/category/new";
        }
    }

    @GetMapping("/{categoryId}/edit")
    public String editForm(@PathVariable String bbsMasterId,
                            @PathVariable String categoryId,
                            Model model) {
        BbsCategory c = categoryService.get(categoryId);
        if (c == null || !bbsMasterId.equals(c.getBbsMasterId())) {
            throw new IllegalArgumentException("카테고리를 찾을 수 없습니다.");
        }
        BbsCategorySaveForm form = new BbsCategorySaveForm();
        form.setCategoryId(c.getCategoryId());
        form.setBbsMasterId(c.getBbsMasterId());
        form.setCategoryCode(c.getCategoryCode());
        form.setCategoryName(c.getCategoryName());
        form.setSortOrder(c.getSortOrder());
        form.setUseYn(c.getUseYn());
        model.addAttribute("form", form);
        model.addAttribute("category", c);
        model.addAttribute("mode", "edit");
        return "admin/system/board/category/form";
    }

    @PostMapping("/{categoryId}/edit")
    public String update(@PathVariable String bbsMasterId,
                          @PathVariable String categoryId,
                          @Valid @ModelAttribute("form") BbsCategorySaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          Model model) {
        form.setCategoryId(categoryId);
        form.setBbsMasterId(bbsMasterId);
        if (br.hasErrors()) {
            model.addAttribute("mode", "edit");
            return "admin/system/board/category/form";
        }
        try {
            categoryService.update(form);
            ra.addFlashAttribute("message", "카테고리를 수정했습니다.");
            return "redirect:/admin/system/board/" + bbsMasterId + "/category";
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_CATEGORY_UPDATE_FAIL reason={}", ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/board/" + bbsMasterId + "/category/" + categoryId + "/edit";
        } catch (Exception ex) {
            log.warn("BBS_CATEGORY_UPDATE_ERROR", ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:/admin/system/board/" + bbsMasterId + "/category/" + categoryId + "/edit";
        }
    }

    @PostMapping("/{categoryId}/use")
    public String toggleUse(@PathVariable String bbsMasterId,
                             @PathVariable String categoryId,
                             @RequestParam("active") boolean active,
                             RedirectAttributes ra) {
        try {
            categoryService.toggleUse(categoryId, active);
            ra.addFlashAttribute("message",
                active ? "카테고리를 사용으로 전환했습니다." : "카테고리를 사용중지했습니다.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/system/board/" + bbsMasterId + "/category";
    }

    @PostMapping("/{categoryId}/delete")
    public String delete(@PathVariable String bbsMasterId,
                          @PathVariable String categoryId,
                          RedirectAttributes ra) {
        try {
            categoryService.softDelete(categoryId);
            ra.addFlashAttribute("message", "카테고리를 삭제했습니다.");
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_CATEGORY_DELETE_FAIL reason={}", ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            log.warn("BBS_CATEGORY_DELETE_ERROR", ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/system/board/" + bbsMasterId + "/category";
    }
}
