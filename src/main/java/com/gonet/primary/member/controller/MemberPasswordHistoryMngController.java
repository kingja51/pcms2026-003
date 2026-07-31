package com.gonet.primary.member.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.member.dto.MemberPasswordHistory;
import com.gonet.primary.member.dto.MemberPasswordHistorySearch;
import com.gonet.primary.member.service.MemberPasswordHistoryMngService;
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
 * 회원 비밀번호 이력 — {@code /admin/system/member/password-history}.
 *
 * <p>read-only. password_hash 는 BCrypt 라 화면에 마스킹 표시 (***).
 */
@Controller
@RequestMapping("/admin/system/member/password-history")
public class MemberPasswordHistoryMngController {

    private static final Logger log = LoggerFactory.getLogger(MemberPasswordHistoryMngController.class);

    private final MemberPasswordHistoryMngService service;
    private final AuditLogger          auditLogger;
    private final ExcelResponseWriter  excelWriter;

    public MemberPasswordHistoryMngController(MemberPasswordHistoryMngService service,
                                                AuditLogger auditLogger,
                                                ExcelResponseWriter excelWriter) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    @GetMapping
    public String list(@ModelAttribute("search") MemberPasswordHistorySearch search, Model model) {
        List<MemberPasswordHistory> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/member/password-history/list";
    }

    @GetMapping("/{pwdHistoryId}")
    public String detail(@PathVariable String pwdHistoryId, Model model, RedirectAttributes ra) {
        MemberPasswordHistory row = service.get(pwdHistoryId);
        if (row == null) {
            ra.addFlashAttribute("error", "비밀번호 이력을 찾을 수 없습니다.");
            return "redirect:/admin/system/member/password-history";
        }
        model.addAttribute("row", row);
        return "admin/system/member/password-history/detail";
    }

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") MemberPasswordHistorySearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/member/password-history";
        }
        List<MemberPasswordHistory> rows = service.findForExport(search);

        // password_hash 는 평문 노출 절대 금지 — 엑셀에도 길이만 표시
        String filename = excelWriter.write(res, "회원비밀번호이력", "gopcms_member_pwd_history",
            new String[]{ "이력ID", "회원ID", "변경일시", "해시길이", "등록자IP" },
            rows,
            (row, h) -> {
                row.createCell(0).setCellValue(nvl(h.getPwdHistoryId()));
                row.createCell(1).setCellValue(nvl(h.getMemberId()));
                row.createCell(2).setCellValue(str(h.getChangedAt()));
                row.createCell(3).setCellValue(h.getPasswordHash() == null ? 0 : h.getPasswordHash().length());
                row.createCell(4).setCellValue(nvl(h.getCreatedIp()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_member_password_history")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size() + ",\"filename\":" + JsonUtils.quote(filename)
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===MEMBER_PWD_HIST_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }
}
