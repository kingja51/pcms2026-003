package com.gonet.primary.popup.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.service.FileService;
import com.gonet.primary.popup.dto.Popup;
import com.gonet.primary.popup.dto.PopupSaveForm;
import com.gonet.primary.popup.dto.PopupSearch;
import com.gonet.primary.popup.dto.PopupType;
import com.gonet.primary.popup.service.PopupService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 팝업 관리 Controller — CRUD + 사용중지 토글.
 *
 * <p>URL prefix: {@code /admin/system/popup}.
 */
@Controller
@RequestMapping("/admin/system/popup")
public class PopupMngController {

    private static final Logger log = LoggerFactory.getLogger(PopupMngController.class);

    private final PopupService service;
    private final SiteService  siteService;
    private final FileService  fileService;

    public PopupMngController(PopupService service, SiteService siteService, FileService fileService) {
        this.service     = service;
        this.siteService = siteService;
        this.fileService = fileService;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    @GetMapping
    public String list(@ModelAttribute("search") PopupSearch search, Model model) {
        List<Popup> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("sites", listSites());
        model.addAttribute("popupTypes", PopupType.values());
        return "admin/system/popup/list";
    }

    @GetMapping("/{popupId}")
    public String detail(@PathVariable String popupId, Model model, RedirectAttributes ra) {
        Popup p = service.get(popupId);
        if (p == null) {
            ra.addFlashAttribute("error", "팝업을 찾을 수 없습니다.");
            return "redirect:/admin/system/popup";
        }
        model.addAttribute("popup", p);
        // 그룹 내 활성 파일 (첫 파일이 썸네일 표시용)
        List<FileItem> files = fileService.listGroupFiles(p.getFileGroupId());
        model.addAttribute("files", files);
        if (!files.isEmpty()) {
            model.addAttribute("thumbFileId", files.get(0).getFileId());
        }
        return "admin/system/popup/detail";
    }

    // ==================================================================
    // Create / Update 폼
    // ==================================================================

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            PopupSaveForm form = new PopupSaveForm();
            form.setPopupType("LAYER");
            form.setUseYn("Y");
            form.setCookieDays(1);
            // file-picker 가 entityId 로 사용 — 사전 발급. 폼 취소 시 orphan file 은 향후 정리 스케줄러 대상
            form.setPopupId(UuidV7Generator.generate("POP"));
            // file-picker 의 file_group_id 사전 발급 — UPSERT 로 동시 업로드 race 흡수
            form.setFileGroupId(UuidV7Generator.generate("FG0"));
            model.addAttribute("form", form);
        }
        // picker 업로드 그룹(model)과 폼 제출 그룹(hidden *{fileGroupId})은 반드시 동일해야 함 —
        // 별도 발급하면 이미지가 업로드된 그룹과 팝업이 저장하는 그룹이 갈라져 영구 미표시.
        PopupSaveForm current = (PopupSaveForm) model.getAttribute("form");
        model.addAttribute("fileGroupId", current.getFileGroupId());
        model.addAttribute("sites", listSites());
        model.addAttribute("popupTypes", PopupType.values());
        model.addAttribute("mode", "create");
        return "admin/system/popup/form";
    }

    @GetMapping("/{popupId}/edit")
    public String editForm(@PathVariable String popupId, Model model, RedirectAttributes ra) {
        Popup p = service.get(popupId);
        if (p == null) {
            ra.addFlashAttribute("error", "팝업을 찾을 수 없습니다.");
            return "redirect:/admin/system/popup";
        }
        PopupSaveForm form = toForm(p);
        // 기존 그룹이 없는 레거시 데이터는 즉석 발급 — UPSERT 가 흡수
        if (form.getFileGroupId() == null || form.getFileGroupId().isBlank()) {
            form.setFileGroupId(UuidV7Generator.generate("FG0"));
        }
        model.addAttribute("form", form);
        model.addAttribute("fileGroupId", form.getFileGroupId());
        // 기존 업로드 파일 목록 — picker 가 업로드 영역 아래 KRDS 파일 리스트로 표시
        model.addAttribute("existingFiles", fileService.listGroupFiles(form.getFileGroupId()));
        model.addAttribute("sites", listSites());
        model.addAttribute("popupTypes", PopupType.values());
        model.addAttribute("mode", "edit");
        return "admin/system/popup/form";
    }

    // ==================================================================
    // CUD
    // ==================================================================

    @PostMapping
    public String create(@Valid @ModelAttribute("form") PopupSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/popup/new";
        }
        try {
            String id = service.create(form);
            log.info("===POPUP_CREATE ok id={} title={}", id, form.getPopupTitle());
            ra.addFlashAttribute("message", "팝업을 등록했습니다.");
            String target = "/admin/system/popup/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("POPUP_CREATE fail title={} reason={}", form.getPopupTitle(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/popup/new";
        } catch (Exception ex) {
            log.warn("POPUP_CREATE error title={}", form.getPopupTitle(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/popup/new";
        }
    }

    @PostMapping("/{popupId}")
    public String update(@PathVariable String popupId,
                          @Valid @ModelAttribute("form") PopupSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setPopupId(popupId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/popup/" + popupId + "/edit";
        }
        try {
            service.update(form);
            log.info("===POPUP_UPDATE ok id={}", popupId);
            ra.addFlashAttribute("message", "팝업을 수정했습니다.");
            String target = "/admin/system/popup/" + popupId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("POPUP_UPDATE fail id={} reason={}", popupId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/popup/" + popupId + "/edit";
        } catch (Exception ex) {
            log.warn("POPUP_UPDATE error id={}", popupId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/popup/" + popupId + "/edit";
        }
    }

    @PostMapping("/{popupId}/use")
    public String toggleUse(@PathVariable String popupId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/popup/" + popupId;
        try {
            service.toggleUse(popupId, active);
            ra.addFlashAttribute("message", active ? "팝업을 사용 처리했습니다." : "팝업을 사용중지했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("POPUP_USE_TOGGLE error id={}", popupId, ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{popupId}/delete")
    public String delete(@PathVariable String popupId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(popupId);
            log.info("===POPUP_DELETE ok id={}", popupId);
            ra.addFlashAttribute("message", "팝업을 삭제했습니다.");
            String target = "/admin/system/popup";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/popup/" + popupId;
        } catch (Exception ex) {
            log.warn("POPUP_DELETE error id={}", popupId, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/popup/" + popupId;
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private List<Site> listSites() {
        SiteSearch ss = new SiteSearch();
        ss.setPage(1);
        ss.setPageSizeUnbounded(500);
        return siteService.search(ss);
    }

    private PopupSaveForm toForm(Popup p) {
        PopupSaveForm f = new PopupSaveForm();
        f.setPopupId(p.getPopupId());
        f.setSiteId(p.getSiteId());
        f.setPopupTitle(p.getPopupTitle());
        f.setPopupContent(p.getPopupContent());
        f.setPopupType(p.getPopupType());
        f.setLinkUrl(p.getLinkUrl());
        f.setFileGroupId(p.getFileGroupId());
        f.setWidthPx(p.getWidthPx());
        f.setHeightPx(p.getHeightPx());
        f.setPositionX(p.getPositionX());
        f.setPositionY(p.getPositionY());
        f.setShowFrom(p.getShowFrom());
        f.setShowTo(p.getShowTo());
        f.setShowDays(p.getShowDays());
        f.setShowTimeFrom(p.getShowTimeFrom());
        f.setShowTimeTo(p.getShowTimeTo());
        f.setCookieDays(p.getCookieDays());
        f.setSortOrder(p.getSortOrder());
        f.setUseYn(p.getUseYn());
        return f;
    }
}
