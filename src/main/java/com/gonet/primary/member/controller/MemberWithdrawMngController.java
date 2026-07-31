package com.gonet.primary.member.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.member.dto.MemberWithdraw;
import com.gonet.primary.member.dto.MemberWithdrawSearch;
import com.gonet.primary.member.service.MemberWithdrawMngService;
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
 * 탈퇴 회원 — {@code /admin/system/member/withdraw}.
 *
 * <p>법정 최소 항목만 보관 (login_id_hash / di_hash). 평문 PII 없음. read-only.
 * retention_expire_at 만료 후 WithdrawPurgeScheduler 가 영구 삭제 (§0.38).
 */
@Controller
@RequestMapping("/admin/system/member/withdraw")
public class MemberWithdrawMngController {

    private static final Logger log = LoggerFactory.getLogger(MemberWithdrawMngController.class);

    private final MemberWithdrawMngService service;
    private final AuditLogger              auditLogger;
    private final ExcelResponseWriter      excelWriter;

    public MemberWithdrawMngController(MemberWithdrawMngService service,
                                        AuditLogger auditLogger,
                                        ExcelResponseWriter excelWriter) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    @GetMapping
    public String list(@ModelAttribute("search") MemberWithdrawSearch search, Model model) {
        List<MemberWithdraw> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/member/withdraw/list";
    }

    @GetMapping("/{memberId}")
    public String detail(@PathVariable String memberId, Model model, RedirectAttributes ra) {
        MemberWithdraw row = service.get(memberId);
        if (row == null) {
            ra.addFlashAttribute("error", "탈퇴 이력을 찾을 수 없습니다.");
            return "redirect:/admin/system/member/withdraw";
        }
        model.addAttribute("row", row);
        return "admin/system/member/withdraw/detail";
    }

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") MemberWithdrawSearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/member/withdraw";
        }
        List<MemberWithdraw> rows = service.findForExport(search);

        String filename = excelWriter.write(res, "탈퇴회원", "gopcms_member_withdraw",
            new String[]{ "회원ID", "사이트", "탈퇴일시", "구분(status)", "실제사유(reason)",
                          "보관만료", "법적근거", "처리자", "처리IP", "원가입자", "loginIdHash", "diHash" },
            rows,
            (row, w) -> {
                row.createCell(0).setCellValue(nvl(w.getMemberId()));
                row.createCell(1).setCellValue(nvl(w.getSiteCode()));
                row.createCell(2).setCellValue(str(w.getWithdrawAt()));
                row.createCell(3).setCellValue(nvl(w.getWithdrawStatus()));
                row.createCell(4).setCellValue(nvl(w.getWithdrawReason()));
                row.createCell(5).setCellValue(str(w.getRetentionExpireAt()));
                row.createCell(6).setCellValue(nvl(w.getLegalBasis()));
                row.createCell(7).setCellValue(nvl(w.getUpdatedBy()));
                row.createCell(8).setCellValue(nvl(w.getUpdatedIp()));
                row.createCell(9).setCellValue(nvl(w.getCreatedBy()));
                row.createCell(10).setCellValue(nvl(w.getLoginIdHash()));
                row.createCell(11).setCellValue(nvl(w.getDiHash()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_member_withdraw")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size() + ",\"filename\":" + JsonUtils.quote(filename)
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===MEMBER_WITHDRAW_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }
}
