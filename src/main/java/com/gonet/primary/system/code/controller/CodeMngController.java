package com.gonet.primary.system.code.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.system.code.dto.Code;
import com.gonet.primary.system.code.dto.CodeGroup;
import com.gonet.primary.system.code.dto.CodeSaveForm;
import com.gonet.primary.system.code.dto.CodeSearch;
import com.gonet.primary.system.code.service.CodeGroupService;
import com.gonet.primary.system.code.service.CodeService;
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
 * 공통코드 CRUD — 그룹 종속. {@code /admin/system/code/{codeGroupId}/items}.
 */
@Controller
@RequestMapping("/admin/system/code/{codeGroupId}/items")
public class CodeMngController {

    private static final Logger log = LoggerFactory.getLogger(CodeMngController.class);

    private final CodeService          service;
    private final CodeGroupService     groupService;
    private final AuditLogger          auditLogger;
    private final ExcelResponseWriter  excelWriter;

    public CodeMngController(CodeService service, CodeGroupService groupService,
                              AuditLogger auditLogger, ExcelResponseWriter excelWriter) {
        this.service = service;
        this.groupService = groupService;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    @GetMapping
    public String list(@PathVariable String codeGroupId,
                        @ModelAttribute("search") CodeSearch search,
                        Model model, RedirectAttributes ra) {
        CodeGroup g = groupService.get(codeGroupId);
        if (g == null) {
            ra.addFlashAttribute("error", "그룹을 찾을 수 없습니다.");
            return "redirect:/admin/system/code";
        }
        search.setCodeGroupId(codeGroupId);
        List<Code> rows = service.search(search);
        int total = service.count(search);

        model.addAttribute("group", g);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/code/item/list";
    }

    @GetMapping("/new")
    public String createForm(@PathVariable String codeGroupId, Model model, RedirectAttributes ra) {
        CodeGroup g = groupService.get(codeGroupId);
        if (g == null) {
            ra.addFlashAttribute("error", "그룹을 찾을 수 없습니다.");
            return "redirect:/admin/system/code";
        }
        if (!model.containsAttribute("form")) {
            CodeSaveForm f = new CodeSaveForm();
            f.setCodeGroupId(codeGroupId);
            model.addAttribute("form", f);
        }
        model.addAttribute("group", g);
        model.addAttribute("mode", "create");
        return "admin/system/code/item/form";
    }

    @GetMapping("/{codeId}/edit")
    public String editForm(@PathVariable String codeGroupId, @PathVariable String codeId,
                            Model model, RedirectAttributes ra) {
        CodeGroup g = groupService.get(codeGroupId);
        Code c = service.get(codeId);
        if (g == null || c == null || !codeGroupId.equals(c.getCodeGroupId())) {
            ra.addFlashAttribute("error", "코드를 찾을 수 없습니다.");
            return "redirect:/admin/system/code/" + codeGroupId + "/items";
        }
        model.addAttribute("group", g);
        model.addAttribute("form", toForm(c));
        model.addAttribute("mode", "edit");
        return "admin/system/code/item/form";
    }

    @PostMapping
    public String create(@PathVariable String codeGroupId,
                          @Valid @ModelAttribute("form") CodeSaveForm form,
                          BindingResult br, RedirectAttributes ra, HttpServletResponse res) {
        form.setCodeGroupId(codeGroupId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/code/" + codeGroupId + "/items/new";
        }
        try {
            String id = service.create(form);
            log.info("===CODE_CREATE ok id={} groupId={} code={}", id, codeGroupId, form.getCode());
            ra.addFlashAttribute("message", "코드를 등록했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/code/" + codeGroupId + "/items");
            return "redirect:/admin/system/code/" + codeGroupId + "/items";
        } catch (IllegalArgumentException ex) {
            log.warn("CODE_CREATE fail groupId={} reason={}", codeGroupId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/code/" + codeGroupId + "/items/new";
        } catch (Exception ex) {
            log.warn("CODE_CREATE error groupId={} reason={}", codeGroupId, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "등록 중 오류가 발생했습니다.");
            return "redirect:/admin/system/code/" + codeGroupId + "/items/new";
        }
    }

    @PostMapping("/{codeId}")
    public String update(@PathVariable String codeGroupId, @PathVariable String codeId,
                          @Valid @ModelAttribute("form") CodeSaveForm form,
                          BindingResult br, RedirectAttributes ra, HttpServletResponse res) {
        form.setCodeId(codeId);
        form.setCodeGroupId(codeGroupId);
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/code/" + codeGroupId + "/items/" + codeId + "/edit";
        }
        try {
            service.update(form);
            log.info("===CODE_UPDATE ok id={}", codeId);
            ra.addFlashAttribute("message", "코드를 수정했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/code/" + codeGroupId + "/items");
            return "redirect:/admin/system/code/" + codeGroupId + "/items";
        } catch (IllegalArgumentException ex) {
            log.warn("CODE_UPDATE fail id={} reason={}", codeId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/code/" + codeGroupId + "/items/" + codeId + "/edit";
        } catch (Exception ex) {
            log.warn("CODE_UPDATE error id={} reason={}", codeId, ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/admin/system/code/" + codeGroupId + "/items/" + codeId + "/edit";
        }
    }

    @DeleteMapping("/{codeId}")
    public String delete(@PathVariable String codeGroupId, @PathVariable String codeId,
                          RedirectAttributes ra, HttpServletResponse res) {
        try {
            service.softDelete(codeId);
            log.info("===CODE_DELETE ok id={}", codeId);
            ra.addFlashAttribute("message", "코드를 삭제했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/code/" + codeGroupId + "/items");
            return "redirect:/admin/system/code/" + codeGroupId + "/items";
        } catch (IllegalArgumentException ex) {
            log.warn("CODE_DELETE fail id={} reason={}", codeId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/code/" + codeGroupId + "/items";
        } catch (Exception ex) {
            log.warn("CODE_DELETE error id={} reason={}", codeId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/code/" + codeGroupId + "/items";
        }
    }

    // ==================================================================
    // Excel
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@PathVariable String codeGroupId,
                         @Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") CodeSearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/code/" + codeGroupId + "/items";
        }
        search.setCodeGroupId(codeGroupId);
        search.setPage(1);
        search.setPageSize(10_000);
        List<Code> rows = service.search(search);

        String excelFilename = excelWriter.write(res, "코드", "gopcms_codes_" + codeGroupId,
            new String[]{ "코드ID", "그룹코드", "코드", "코드명", "부가값", "정렬", "사용여부", "등록일시" },
            rows,
            (row, c) -> {
                row.createCell(0).setCellValue(nvl(c.getCodeId()));
                row.createCell(1).setCellValue(nvl(c.getGroupCode()));
                row.createCell(2).setCellValue(nvl(c.getCode()));
                row.createCell(3).setCellValue(nvl(c.getCodeName()));
                row.createCell(4).setCellValue(nvl(c.getCodeValue()));
                row.createCell(5).setCellValue(c.getSortOrder() == null ? 0 : c.getSortOrder());
                row.createCell(6).setCellValue(nvl(c.getUseYn()));
                row.createCell(7).setCellValue(str(c.getCreatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_code")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withTarget(codeGroupId)
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size()+",\"filename\":" + excelFilename+",\"keyword\":" + search.getKeyword()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===CODE_EXCEL ok groupId={} count={} reason='{}'",
            codeGroupId, rows.size(), excelReq.getDownloadReason());
        return null;
    }

    // ------------------------------------------------------------------
    private CodeSaveForm toForm(Code c) {
        CodeSaveForm f = new CodeSaveForm();
        f.setCodeId(c.getCodeId());
        f.setCodeGroupId(c.getCodeGroupId());
        f.setCode(c.getCode());
        f.setCodeName(c.getCodeName());
        f.setCodeValue(c.getCodeValue());
        f.setSortOrder(c.getSortOrder());
        f.setUseYn(c.getUseYn());
        return f;
    }
}
