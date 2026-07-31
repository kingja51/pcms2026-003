package com.gonet.primary.system.code.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.system.code.dto.CodeGroup;
import com.gonet.primary.system.code.dto.CodeGroupSaveForm;
import com.gonet.primary.system.code.dto.CodeGroupSearch;
import com.gonet.primary.system.code.service.CodeGroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

import static com.gonet.common.excel.ExcelResponseWriter.nvl;
import static com.gonet.common.excel.ExcelResponseWriter.str;

/**
 * 공통코드 그룹 CRUD — {@code /admin/system/code}.
 */
@Controller
@RequestMapping("/admin/system/code")
public class CodeGroupMngController {

    private static final Logger log = LoggerFactory.getLogger(CodeGroupMngController.class);

    private final CodeGroupService     service;
    private final AuditLogger          auditLogger;
    private final ExcelResponseWriter  excelWriter;

    public CodeGroupMngController(CodeGroupService service,
                                   AuditLogger auditLogger,
                                   ExcelResponseWriter excelWriter) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    @GetMapping
    public String list(@ModelAttribute("search") CodeGroupSearch search, Model model) {
        List<CodeGroup> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/code/list";
    }

    @GetMapping("/{codeGroupId}")
    public String detail(@PathVariable String codeGroupId, Model model, RedirectAttributes ra) {
        CodeGroup g = service.get(codeGroupId);
        if (g == null) {
            ra.addFlashAttribute("error", "그룹을 찾을 수 없습니다.");
            return "redirect:/admin/system/code";
        }
        model.addAttribute("group", g);
        return "admin/system/code/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CodeGroupSaveForm());
        }
        model.addAttribute("mode", "create");
        return "admin/system/code/form";
    }

    @GetMapping("/{codeGroupId}/edit")
    public String editForm(@PathVariable String codeGroupId, Model model, RedirectAttributes ra) {
        CodeGroup g = service.get(codeGroupId);
        if (g == null) {
            ra.addFlashAttribute("error", "그룹을 찾을 수 없습니다.");
            return "redirect:/admin/system/code";
        }
        model.addAttribute("form", toForm(g));
        model.addAttribute("mode", "edit");
        return "admin/system/code/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CodeGroupSaveForm form,
                          BindingResult br, RedirectAttributes ra, HttpServletResponse res) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/code/new";
        }
        try {
            String id = service.create(form);
            log.info("===CODE_GROUP_CREATE ok id={} code={}", id, form.getGroupCode());
            ra.addFlashAttribute("message", "코드그룹을 등록했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/code/" + id);
            return "redirect:/admin/system/code/" + id;
        } catch (IllegalArgumentException ex) {
            log.warn("CODE_GROUP_CREATE fail reason={}", ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/code/new";
        } catch (Exception ex) {
            log.warn("CODE_GROUP_CREATE error reason={}", ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/code/new";
        }
    }

    @PostMapping("/{codeGroupId}")
    public String update(@PathVariable String codeGroupId,
                          @Valid @ModelAttribute("form") CodeGroupSaveForm form,
                          BindingResult br, RedirectAttributes ra, HttpServletResponse res) {
        form.setCodeGroupId(codeGroupId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/code/" + codeGroupId + "/edit";
        }
        try {
            service.update(form);
            log.info("===CODE_GROUP_UPDATE ok id={}", codeGroupId);
            ra.addFlashAttribute("message", "코드그룹을 수정했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/code/" + codeGroupId);
            return "redirect:/admin/system/code/" + codeGroupId;
        } catch (IllegalArgumentException ex) {
            log.warn("CODE_GROUP_UPDATE fail id={} reason={}", codeGroupId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/code/" + codeGroupId + "/edit";
        } catch (Exception ex) {
            log.warn("CODE_GROUP_UPDATE error id={} reason={}", codeGroupId, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/code/" + codeGroupId + "/edit";
        }
    }

    @DeleteMapping("/{codeGroupId}")
    public String delete(@PathVariable String codeGroupId, RedirectAttributes ra, HttpServletResponse res) {
        try {
            service.softDelete(codeGroupId);
            log.info("===CODE_GROUP_DELETE ok id={}", codeGroupId);
            ra.addFlashAttribute("message", "코드그룹을 삭제했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/code");
            return "redirect:/admin/system/code";
        } catch (IllegalArgumentException ex) {
            log.warn("CODE_GROUP_DELETE fail id={} reason={}", codeGroupId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/code/" + codeGroupId;
        } catch (Exception ex) {
            log.warn("CODE_GROUP_DELETE error id={} reason={}", codeGroupId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/code/" + codeGroupId;
        }
    }

    // ==================================================================
    // Excel
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") CodeGroupSearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/code";
        }
        search.setPage(1);
        search.setPageSize(10_000);
        List<CodeGroup> rows = service.search(search);

        String excelFilename = excelWriter.write(res, "코드그룹", "gopcms_code_groups",
            new String[]{ "그룹ID", "그룹코드", "그룹명", "항목수", "사용여부", "설명", "등록일시" },
            rows,
            (row, g) -> {
                row.createCell(0).setCellValue(nvl(g.getCodeGroupId()));
                row.createCell(1).setCellValue(nvl(g.getGroupCode()));
                row.createCell(2).setCellValue(nvl(g.getGroupName()));
                row.createCell(3).setCellValue(g.getItemCount() == null ? 0 : g.getItemCount());
                row.createCell(4).setCellValue(nvl(g.getUseYn()));
                row.createCell(5).setCellValue(nvl(g.getDescription()));
                row.createCell(6).setCellValue(str(g.getCreatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_code_group")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size()+",\"filename\":" + excelFilename+",\"keyword\":" + search.getKeyword()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===CODE_GROUP_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }

    // ------------------------------------------------------------------
    private CodeGroupSaveForm toForm(CodeGroup g) {
        CodeGroupSaveForm f = new CodeGroupSaveForm();
        f.setCodeGroupId(g.getCodeGroupId());
        f.setGroupCode(g.getGroupCode());
        f.setGroupName(g.getGroupName());
        f.setDescription(g.getDescription());
        f.setUseYn(g.getUseYn());
        return f;
    }
}
