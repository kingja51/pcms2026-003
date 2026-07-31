package com.gonet.primary.member.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.member.dto.MemberConsent;
import com.gonet.primary.member.dto.MemberConsentSearch;
import com.gonet.primary.member.service.MemberConsentMngService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

import static com.gonet.common.excel.ExcelResponseWriter.nvl;
import static com.gonet.common.excel.ExcelResponseWriter.str;

/**
 * 회원 동의 이력 관리자 화면 — {@code /admin/system/member/consent}.
 *
 * <p>read-only — 동의 이력은 immutable audit. 관리자는 검색·단건 조회·엑셀만 가능.
 */
@Controller
@RequestMapping("/admin/system/member/consent")
public class MemberConsentMngController {

    private static final Logger log = LoggerFactory.getLogger(MemberConsentMngController.class);

    private final MemberConsentMngService service;
    private final AuditLogger             auditLogger;
    private final ExcelResponseWriter     excelWriter;

    public MemberConsentMngController(MemberConsentMngService service,
                                       AuditLogger auditLogger,
                                       ExcelResponseWriter excelWriter) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    @GetMapping
    public String list(@ModelAttribute("search") MemberConsentSearch search, Model model) {
        List<MemberConsent> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/member/consent/list";
    }

    @GetMapping("/{memberConsentId}")
    public String detail(@PathVariable String memberConsentId,
                          Model model, RedirectAttributes ra) {
        MemberConsent row = service.get(memberConsentId);
        if (row == null) {
            ra.addFlashAttribute("error", "동의 이력을 찾을 수 없습니다.");
            return "redirect:/admin/system/member/consent";
        }
        model.addAttribute("row", row);
        return "admin/system/member/consent/detail";
    }

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") MemberConsentSearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/member/consent";
        }
        List<MemberConsent> rows = service.findForExport(search);

        String filename = excelWriter.write(res, "회원동의이력", "gopcms_member_consent",
            new String[]{ "이력ID", "회원ID", "유형", "버전", "동의여부", "동의일시", "IP", "User-Agent" },
            rows,
            (row, c) -> {
                row.createCell(0).setCellValue(nvl(c.getMemberConsentId()));
                row.createCell(1).setCellValue(nvl(c.getMemberId()));
                row.createCell(2).setCellValue(nvl(c.getConsentType()));
                row.createCell(3).setCellValue(nvl(c.getConsentVersion()));
                row.createCell(4).setCellValue(nvl(c.getAgreeYn()));
                row.createCell(5).setCellValue(str(c.getAgreedAt()));
                row.createCell(6).setCellValue(nvl(c.getClientIp()));
                row.createCell(7).setCellValue(nvl(c.getUserAgent()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_member_consent")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size() + ",\"filename\":" + JsonUtils.quote(filename)
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===MEMBER_CONSENT_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }
}
