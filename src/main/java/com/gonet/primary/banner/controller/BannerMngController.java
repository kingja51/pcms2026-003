package com.gonet.primary.banner.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.primary.banner.dto.Banner;
import com.gonet.primary.banner.dto.BannerSaveForm;
import com.gonet.primary.banner.dto.BannerSearch;
import com.gonet.primary.banner.service.BannerService;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.service.FileService;
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

@Controller
@RequestMapping("/admin/system/banner")
public class BannerMngController {

    private static final Logger log = LoggerFactory.getLogger(BannerMngController.class);

    private final BannerService service;
    private final SiteService   siteService;
    private final FileService   fileService;

    public BannerMngController(BannerService service, SiteService siteService, FileService fileService) {
        this.service     = service;
        this.siteService = siteService;
        this.fileService = fileService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") BannerSearch search, Model model) {
        List<Banner> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("sites", listSites());
        return "admin/system/banner/list";
    }

    @GetMapping("/{bannerId}")
    public String detail(@PathVariable String bannerId, Model model, RedirectAttributes ra) {
        Banner b = service.get(bannerId);
        if (b == null) {
            ra.addFlashAttribute("error", "배너를 찾을 수 없습니다.");
            return "redirect:/admin/system/banner";
        }
        model.addAttribute("banner", b);
        // 그룹 내 활성 파일 (첫 파일이 썸네일 표시용)
        List<FileItem> files = fileService.listGroupFiles(b.getFileGroupId());
        model.addAttribute("files", files);
        if (!files.isEmpty()) {
            model.addAttribute("thumbFileId", files.get(0).getFileId());
        }
        return "admin/system/banner/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            BannerSaveForm form = new BannerSaveForm();
            form.setUseYn("Y");
            form.setLinkTarget("_self");
            form.setBannerLocation("HEADER");
            form.setBannerId(UuidV7Generator.generate("BAN"));
            // file-picker 의 file_group_id 사전 발급 — UPSERT 로 동시 업로드 race 흡수
            form.setFileGroupId(UuidV7Generator.generate("FG0"));
            model.addAttribute("form", form);
        }
        // picker 업로드 그룹(model)과 폼 제출 그룹(hidden *{fileGroupId})은 반드시 동일해야 함 —
        // 별도 발급하면 이미지가 업로드된 그룹과 배너가 저장하는 그룹이 갈라져 영구 미표시.
        BannerSaveForm current = (BannerSaveForm) model.getAttribute("form");
        model.addAttribute("fileGroupId", current.getFileGroupId());
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "create");
        return "admin/system/banner/form";
    }

    @GetMapping("/{bannerId}/edit")
    public String editForm(@PathVariable String bannerId, Model model, RedirectAttributes ra) {
        Banner b = service.get(bannerId);
        if (b == null) {
            ra.addFlashAttribute("error", "배너를 찾을 수 없습니다.");
            return "redirect:/admin/system/banner";
        }
        BannerSaveForm form = toForm(b);
        // 기존 그룹이 없는 레거시 데이터는 즉석 발급 — UPSERT 가 흡수
        if (form.getFileGroupId() == null || form.getFileGroupId().isBlank()) {
            form.setFileGroupId(UuidV7Generator.generate("FG0"));
        }
        model.addAttribute("form", form);
        model.addAttribute("fileGroupId", form.getFileGroupId());
        // 기존 업로드 파일 목록 — picker 가 업로드 영역 아래 KRDS 파일 리스트로 표시
        model.addAttribute("existingFiles", fileService.listGroupFiles(form.getFileGroupId()));
        model.addAttribute("sites", listSites());
        model.addAttribute("mode", "edit");
        return "admin/system/banner/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BannerSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/banner/new";
        }
        try {
            String id = service.create(form);
            log.info("===BANNER_CREATE ok id={} title={}", id, form.getBannerTitle());
            ra.addFlashAttribute("message", "배너를 등록했습니다.");
            String target = "/admin/system/banner/" + id;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/banner/new";
        } catch (Exception ex) {
            log.warn("BANNER_CREATE error title={}", form.getBannerTitle(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/banner/new";
        }
    }

    @PostMapping("/{bannerId}")
    public String update(@PathVariable String bannerId,
                          @Valid @ModelAttribute("form") BannerSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setBannerId(bannerId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/banner/" + bannerId + "/edit";
        }
        try {
            service.update(form);
            ra.addFlashAttribute("message", "배너를 수정했습니다.");
            String target = "/admin/system/banner/" + bannerId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/banner/" + bannerId + "/edit";
        } catch (Exception ex) {
            log.warn("BANNER_UPDATE error id={}", bannerId, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/banner/" + bannerId + "/edit";
        }
    }

    @PostMapping("/{bannerId}/use")
    public String toggleUse(@PathVariable String bannerId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        String redirect = "/admin/system/banner/" + bannerId;
        try {
            service.toggleUse(bannerId, active);
            ra.addFlashAttribute("message", active ? "배너를 사용 처리했습니다." : "배너를 사용중지했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("BANNER_USE_TOGGLE error id={}", bannerId, ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{bannerId}/delete")
    public String delete(@PathVariable String bannerId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(bannerId);
            ra.addFlashAttribute("message", "배너를 삭제했습니다.");
            String target = "/admin/system/banner";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (Exception ex) {
            log.warn("BANNER_DELETE error id={}", bannerId, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/banner/" + bannerId;
        }
    }

    private List<Site> listSites() {
        SiteSearch ss = new SiteSearch();
        ss.setPage(1);
        ss.setPageSizeUnbounded(500);
        return siteService.search(ss);
    }

    private BannerSaveForm toForm(Banner b) {
        BannerSaveForm f = new BannerSaveForm();
        f.setBannerId(b.getBannerId());
        f.setSiteId(b.getSiteId());
        f.setBannerTitle(b.getBannerTitle());
        f.setBannerLocation(b.getBannerLocation());
        f.setFileGroupId(b.getFileGroupId());
        f.setAltText(b.getAltText());
        f.setLinkUrl(b.getLinkUrl());
        f.setLinkTarget(b.getLinkTarget());
        f.setShowFrom(b.getShowFrom());
        f.setShowTo(b.getShowTo());
        f.setSortOrder(b.getSortOrder());
        f.setUseYn(b.getUseYn());
        return f;
    }
}
