package com.gonet.primary.system.site.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.service.FileService;
import com.gonet.primary.system.site.dto.Template;
import com.gonet.primary.system.site.dto.TemplateSaveForm;
import com.gonet.primary.system.site.dto.TemplateSearch;
import com.gonet.primary.system.site.service.TemplateService;
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
 * 템플릿 관리 Controller — 전역 카탈로그 CRUD + 엑셀.
 *
 * <p>URL prefix: {@code /admin/system/template} (2026-04-23c 부터 전역).
 *
 * <p>CUD 규약 (try-catch + log.debug/warn + HX-Redirect) 준수.
 */
@Controller
@RequestMapping("/admin/system/template")
public class TemplateMngController {

    private static final Logger log = LoggerFactory.getLogger(TemplateMngController.class);

    private final TemplateService service;
    private final AuditLogger auditLogger;
    private final ExcelResponseWriter excelWriter;
    private final FileService fileService;

    public TemplateMngController(TemplateService service,
                                  AuditLogger auditLogger,
                                  ExcelResponseWriter excelWriter,
                                  FileService fileService) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
        this.fileService = fileService;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    @GetMapping
    public String list(@ModelAttribute("search") TemplateSearch search, Model model) {
        List<Template> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/template/list";
    }

    @GetMapping("/{templateId}")
    public String detail(@PathVariable String templateId,
                          Model model,
                          RedirectAttributes ra) {
        Template t = service.get(templateId);
        if (t == null) {
            ra.addFlashAttribute("error", "템플릿을 찾을 수 없습니다.");
            return "redirect:/admin/system/template";
        }
        model.addAttribute("template", t);
        // 캡쳐 이미지 그룹 — 첫 파일이 썸네일 표시용
        List<FileItem> files = fileService.listGroupFiles(t.getFileGroupId());
        model.addAttribute("files", files);
        if (!files.isEmpty()) {
            model.addAttribute("thumbFileId", files.get(0).getFileId());
        }
        return "admin/system/template/detail";
    }

    // ==================================================================
    // Create / Update 폼
    // ==================================================================

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            TemplateSaveForm form = new TemplateSaveForm();
            // file-picker 의 file_group_id 사전 발급 — UPSERT 로 동시 업로드 race 흡수
            form.setFileGroupId(UuidV7Generator.generate("FG0"));
            model.addAttribute("form", form);
        }
        model.addAttribute("mode", "create");
        return "admin/system/template/form";
    }

    @GetMapping("/{templateId}/edit")
    public String editForm(@PathVariable String templateId,
                            Model model,
                            RedirectAttributes ra) {
        Template t = service.get(templateId);
        if (t == null) {
            ra.addFlashAttribute("error", "템플릿을 찾을 수 없습니다.");
            return "redirect:/admin/system/template";
        }
        TemplateSaveForm form = toForm(t);
        // 기존 그룹이 없는 레거시 데이터는 즉석 발급 — UPSERT 가 흡수
        if (form.getFileGroupId() == null || form.getFileGroupId().isBlank()) {
            form.setFileGroupId(UuidV7Generator.generate("FG0"));
        }
        model.addAttribute("form", form);
        model.addAttribute("existingFiles", fileService.listGroupFiles(t.getFileGroupId()));
        model.addAttribute("mode", "edit");
        return "admin/system/template/form";
    }

    // ==================================================================
    // CUD — try-catch + log + HX-Redirect
    // ==================================================================

    @PostMapping
    public String create(@Valid @ModelAttribute("form") TemplateSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/template/new";
        }
        try {
            String templateId = service.create(form);
            log.info("===TEMPLATE_CREATE ok templateId={} code={}", templateId, form.getTemplateCode());
            ra.addFlashAttribute("message", "템플릿을 등록했습니다.");
            String target = "/admin/system/template/" + templateId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("TEMPLATE_CREATE fail code={} reason={}", form.getTemplateCode(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/template/new";
        } catch (Exception ex) {
            log.warn("TEMPLATE_CREATE error code={} reason={}", form.getTemplateCode(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/template/new";
        }
    }

    @PostMapping("/{templateId}")
    public String update(@PathVariable String templateId,
                          @Valid @ModelAttribute("form") TemplateSaveForm form,
                          BindingResult br,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setTemplateId(templateId);

        log.info("===TEMPLATE_UPDATE_FORM form={}", form);


        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/template/" + templateId + "/edit";
        }
        try {
            service.update(form);
            log.info("===TEMPLATE_UPDATE ok templateId={}", templateId);
            ra.addFlashAttribute("message", "템플릿 정보를 수정했습니다.");
            String target = "/admin/system/template/" + templateId;
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("TEMPLATE_UPDATE fail templateId={} reason={}", templateId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/template/" + templateId + "/edit";
        } catch (Exception ex) {
            log.warn("TEMPLATE_UPDATE error templateId={} reason={}", templateId, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/template/" + templateId + "/edit";
        }
    }

    @DeleteMapping("/{templateId}")
    public String delete(@PathVariable String templateId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(templateId);
            log.info("===TEMPLATE_DELETE ok templateId={}", templateId);
            ra.addFlashAttribute("message", "템플릿을 삭제했습니다.");
            String target = "/admin/system/template";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("TEMPLATE_DELETE fail templateId={} reason={}", templateId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/template/" + templateId;
        } catch (Exception ex) {
            log.warn("TEMPLATE_DELETE error templateId={} reason={}", templateId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/template/" + templateId;
        }
    }

    // ==================================================================
    // Excel
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") TemplateSearch search,
                         HttpServletRequest req,
                         HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/template";
        }
        search.setPage(1);
        search.setPageSize(10_000);
        List<Template> rows = service.search(search);

        String excelFilename = excelWriter.write(res, "템플릿", "gopcms_templates",
            new String[]{ "템플릿ID", "코드", "이름", "레이아웃 경로",
                          "사용여부", "설명", "등록자", "등록일시" },
            rows,
            (row, t) -> {
                row.createCell(0).setCellValue(nvl(t.getTemplateId()));
                row.createCell(1).setCellValue(nvl(t.getTemplateCode()));
                row.createCell(2).setCellValue(nvl(t.getTemplateName()));
                row.createCell(3).setCellValue(nvl(t.getLayoutPath()));
                row.createCell(4).setCellValue(nvl(t.getUseYn()));
                row.createCell(5).setCellValue(nvl(t.getDescription()));
                row.createCell(6).setCellValue(nvl(t.getCreatedBy()));
                row.createCell(7).setCellValue(str(t.getCreatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_template")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size()+",\"filename\":" + excelFilename+",\"keyword\":" + search.getKeyword()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===TEMPLATE_EXCEL ok count={} reason='{}'",
            rows.size(), excelReq.getDownloadReason());
        return null;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private TemplateSaveForm toForm(Template t) {
        TemplateSaveForm f = new TemplateSaveForm();
        f.setTemplateId(t.getTemplateId());
        f.setTemplateCode(t.getTemplateCode());
        f.setTemplateName(t.getTemplateName());
        f.setLayoutPath(t.getLayoutPath());
        f.setDesignMd(t.getDesignMd());
        f.setFileGroupId(t.getFileGroupId());
        f.setDescription(t.getDescription());
        f.setUseYn(t.getUseYn());
        return f;
    }

}
