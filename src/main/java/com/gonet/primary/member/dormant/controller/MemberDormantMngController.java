package com.gonet.primary.member.dormant.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.MaskUtils;
import com.gonet.logging.privacy.dto.PrivacyAccessEvent;
import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import com.gonet.logging.privacy.service.PrivacyAccessLogger;
import com.gonet.primary.member.dormant.dto.DormantSearch;
import com.gonet.primary.member.dormant.service.DormantAdminService;
import com.gonet.primary.member.dto.Member;
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
 * 휴면 회원 — {@code /admin/system/member/dormant}.
 *
 * <p>휴면 회원은 PII 가 분리 보관되어 있으나 운영자 화면에서는 마스킹 후 노출.
 * PIPA — log_privacy_access (24개월 보존, 사유 필수). read-only.
 */
@Controller
@RequestMapping("/admin/system/member/dormant")
public class MemberDormantMngController {

    private static final Logger log = LoggerFactory.getLogger(MemberDormantMngController.class);

    private final DormantAdminService    service;
    private final AuditLogger            auditLogger;
    private final ExcelResponseWriter    excelWriter;
    private final PrivacyAccessLogger    privacyAccessLogger;

    public MemberDormantMngController(DormantAdminService service,
                                       AuditLogger auditLogger,
                                       ExcelResponseWriter excelWriter,
                                       PrivacyAccessLogger privacyAccessLogger) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
        this.privacyAccessLogger = privacyAccessLogger;
    }

    @GetMapping
    public String list(@ModelAttribute("search") DormantSearch search, Model model) {
        List<Member> rows = service.searchDormant(search);
        int total = service.countDormant(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        privacyAccessLogger.write(
            PrivacyAccessEvent.of(PrivacyAccessLog.ACTION_SEARCH, "tb_member_dormant")
                .withCount(rows.size())
                .withFields("name,email,phone"));
        return "admin/system/member/dormant/list";
    }

    @GetMapping("/{memberId}")
    public String detail(@PathVariable String memberId, Model model, RedirectAttributes ra) {
        Member row = service.getDormant(memberId);
        if (row == null) {
            ra.addFlashAttribute("error", "휴면 회원을 찾을 수 없습니다.");
            return "redirect:/admin/system/member/dormant";
        }
        model.addAttribute("row", row);
        privacyAccessLogger.write(
            PrivacyAccessEvent.of(PrivacyAccessLog.ACTION_READ, "tb_member_dormant")
                .withTarget("MEMBER", memberId)
                .withFields("name,email,phone,address"));
        return "admin/system/member/dormant/detail";
    }

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") DormantSearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/member/dormant";
        }
        List<Member> rows = service.findDormantForExport(search);

        String filename = excelWriter.write(res, "휴면회원", "gopcms_member_dormant",
            new String[]{ "회원ID", "사이트", "로그인ID", "이름(마스킹)", "이메일(마스킹)",
                          "전화(마스킹)", "가입유형", "휴면전환일", "최종로그인" },
            rows,
            (row, m) -> {
                row.createCell(0).setCellValue(nvl(m.getMemberId()));
                row.createCell(1).setCellValue(nvl(m.getSiteCode()));
                row.createCell(2).setCellValue(nvl(m.getLoginId()));
                row.createCell(3).setCellValue(nvl(MaskUtils.name(m.getMemberName())));
                row.createCell(4).setCellValue(nvl(MaskUtils.email(m.getEmail())));
                row.createCell(5).setCellValue(nvl(MaskUtils.phone(m.getPhone())));
                row.createCell(6).setCellValue(nvl(m.getJoinType()));
                row.createCell(7).setCellValue(str(m.getDormantAt()));
                row.createCell(8).setCellValue(str(m.getLastLoginAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_member_dormant")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size() + ",\"filename\":" + JsonUtils.quote(filename)
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        privacyAccessLogger.write(
            PrivacyAccessEvent.of(PrivacyAccessLog.ACTION_EXPORT, "tb_member_dormant")
                .withCount(rows.size())
                .withFields("name,email,phone")
                .withReason(excelReq.getDownloadReason()));
        log.info("===MEMBER_DORMANT_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }
}
