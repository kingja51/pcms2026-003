package com.gonet.primary.system.site.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.system.site.dto.Site;
import com.gonet.primary.system.site.dto.SiteSaveForm;
import com.gonet.primary.system.site.dto.SiteSearch;
import com.gonet.primary.system.site.service.SiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

import static com.gonet.common.excel.ExcelResponseWriter.nvl;
import static com.gonet.common.excel.ExcelResponseWriter.str;

/**
 * 사이트 관리 Controller — 관리자 CRUD + 템플릿 선택 + 엑셀 다운로드.
 *
 * <p>접미사 규약: {@code MngController} (관리자 전용).
 * <p>CUD 패턴: try-catch + log.debug/warn + HX-Redirect + RedirectAttributes flash message.
 * <p>권한: {@code /admin/system/site/**} 는 tb_role_url_access 규칙으로 제한 (ROLE_ADMIN 이상).
 */
@Controller
@RequestMapping("/admin/system/site")
public class SiteMngController {

    private static final Logger log = LoggerFactory.getLogger(SiteMngController.class);

    private final SiteService service;
    private final AuditLogger auditLogger;
    private final ExcelResponseWriter excelWriter;

    public SiteMngController(SiteService service, AuditLogger auditLogger,
                              ExcelResponseWriter excelWriter) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    @GetMapping
    public String list(@ModelAttribute("search") SiteSearch search, Model model) {
        List<Site> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/site/list";
    }

    @GetMapping("/{siteId}")
    public String detail(@PathVariable String siteId, Model model, RedirectAttributes ra) {
        Site site = service.get(siteId);
        if (site == null) {
            ra.addFlashAttribute("error", "사이트를 찾을 수 없습니다.");
            return "redirect:/admin/system/site";
        }
        model.addAttribute("site", site);
        model.addAttribute("templates", service.listTemplates(siteId));
        return "admin/system/site/detail";
    }

    // ==================================================================
    // Create / Update 폼
    // ==================================================================

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new SiteSaveForm());
        }
        model.addAttribute("mode", "create");
        model.addAttribute("templates", List.of()); // 신규 사이트는 아직 템플릿 없음
        return "admin/system/site/form";
    }

    @GetMapping("/{siteId}/edit")
    public String editForm(@PathVariable String siteId, Model model, RedirectAttributes ra) {
        Site site = service.get(siteId);
        if (site == null) {
            ra.addFlashAttribute("error", "사이트를 찾을 수 없습니다.");
            return "redirect:/admin/system/site";
        }
        SiteSaveForm form = toForm(site);
        model.addAttribute("form", form);
        model.addAttribute("mode", "edit");
        model.addAttribute("templates", service.listTemplates(siteId));
        return "admin/system/site/form";
    }

    // ==================================================================
    // CUD — try-catch + log + HX-Redirect 패턴
    // ==================================================================

    @PostMapping
    public String create(@Valid @ModelAttribute("form") SiteSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/site/new";
        }
        try {
            String siteId = service.create(form);
            log.info("===SITE_CREATE ok siteId={} code={}", siteId, form.getSiteCode());
            ra.addFlashAttribute("message", "사이트를 등록했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/site/" + siteId);
            return "redirect:/admin/system/site/" + siteId;
        } catch (IllegalArgumentException ex) {
            log.warn("SITE_CREATE fail code={} reason={}", form.getSiteCode(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/site/new";
        } catch (Exception ex) {
            log.warn("SITE_CREATE error code={} reason={}", form.getSiteCode(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/site/new";
        }
    }

    @PostMapping("/{siteId}")
    public String update(@PathVariable String siteId,
                          @Valid @ModelAttribute("form") SiteSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setSiteId(siteId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/site/" + siteId + "/edit";
        }
        try {
            service.update(form);
            log.info("===SITE_UPDATE ok siteId={}", siteId);
            ra.addFlashAttribute("message", "사이트 정보를 수정했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/site/" + siteId);
            return "redirect:/admin/system/site/" + siteId;
        } catch (IllegalArgumentException ex) {
            log.warn("SITE_UPDATE fail siteId={} reason={}", siteId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/site/" + siteId + "/edit";
        } catch (Exception ex) {
            log.warn("SITE_UPDATE error siteId={} reason={}", siteId, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/site/" + siteId + "/edit";
        }
    }

    /**
     * 기본 템플릿 변경 — 사이트 상세 화면의 핵심 기능.
     * <p>경로 {@code /default-template} (TemplateMngController 의 {@code POST /template} 과 분리).
     * <p>form 파라미터로 templateId 만 받아 {@link SiteService#changeDefaultTemplate}.
     */
    @PostMapping("/{siteId}/default-template")
    public String changeTemplate(@PathVariable String siteId,
                                  @RequestParam(required = false) String templateId,
                                  RedirectAttributes ra,
                                  HttpServletResponse res) {
        try {
            service.changeDefaultTemplate(siteId, templateId);
            log.info("===SITE_TEMPLATE_CHANGE ok siteId={} templateId={}", siteId, templateId);
            ra.addFlashAttribute("message",
                templateId == null || templateId.isBlank()
                    ? "기본 템플릿을 해제했습니다."
                    : "기본 템플릿을 변경했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/site/" + siteId);
            return "redirect:/admin/system/site/" + siteId;
        } catch (IllegalArgumentException ex) {
            log.warn("SITE_TEMPLATE_CHANGE fail siteId={} templateId={} reason={}",
                siteId, templateId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/site/" + siteId;
        } catch (Exception ex) {
            log.warn("SITE_TEMPLATE_CHANGE error siteId={} templateId={} reason={}",
                siteId, templateId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "템플릿 변경 중 오류가 발생했습니다.");
            return "redirect:/admin/system/site/" + siteId;
        }
    }

    @DeleteMapping("/{siteId}")
    public String delete(@PathVariable String siteId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(siteId);
            log.info("===SITE_DELETE ok siteId={}", siteId);
            ra.addFlashAttribute("message", "사이트를 삭제했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/site");
            return "redirect:/admin/system/site";
        } catch (IllegalArgumentException ex) {
            log.warn("SITE_DELETE fail siteId={} reason={}", siteId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/site/" + siteId;
        } catch (Exception ex) {
            log.warn("SITE_DELETE error siteId={} reason={}", siteId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/site/" + siteId;
        }
    }

    // ==================================================================
    // Excel Download (관리자 전용)
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") SiteSearch search,
                         HttpServletRequest req,
                         HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/site";
        }
        // 전체 다운로드 — 페이지 제한 해제
        search.setPage(1);
        search.setPageSize(10_000);
        List<Site> rows = service.search(search);

        String excelFilename = excelWriter.write(res, "사이트", "gopcms_sites",
            new String[]{ "사이트ID", "코드", "이름", "도메인", "기본언어",
                          "기본템플릿", "사용여부", "설명", "등록자", "등록일시" },
            rows,
            (row, s) -> {
                row.createCell(0).setCellValue(nvl(s.getSiteId()));
                row.createCell(1).setCellValue(nvl(s.getSiteCode()));
                row.createCell(2).setCellValue(nvl(s.getSiteName()));
                row.createCell(3).setCellValue(nvl(s.getDomain()));
                row.createCell(4).setCellValue(nvl(s.getDefaultLang()));
                row.createCell(5).setCellValue(nvl(s.getDefaultTemplateName()));
                row.createCell(6).setCellValue(nvl(s.getUseYn()));
                row.createCell(7).setCellValue(nvl(s.getDescription()));
                row.createCell(8).setCellValue(nvl(s.getCreatedBy()));
                row.createCell(9).setCellValue(str(s.getCreatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_site")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size()+",\"filename\":" + excelFilename+",\"keyword\":" + search.getKeyword()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===SITE_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private SiteSaveForm toForm(Site s) {
        SiteSaveForm f = new SiteSaveForm();
        f.setSiteId(s.getSiteId());
        f.setSiteCode(s.getSiteCode());
        f.setSiteName(s.getSiteName());
        f.setDomain(s.getDomain());
        f.setDefaultLang(s.getDefaultLang());
        f.setDefaultTemplateId(s.getDefaultTemplateId());
        f.setDescription(s.getDescription());
        f.setHeadMeta(s.getHeadMeta());
        f.setCopyright(s.getCopyright());
        f.setTheme(s.getTheme());
        f.setUseYn(s.getUseYn());
        return f;
    }

}
