package com.gonet.primary.member.dormant.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.member.dormant.dto.DormantNotice;
import com.gonet.primary.member.dormant.dto.DormantNoticeSearch;
import com.gonet.primary.member.dormant.service.DormantAdminService;
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
 * 휴면 안내 발송 이력 — {@code /admin/system/member/dormant-notice}.
 *
 * <p>UNIQUE (member_id, stage) — 같은 단계 중복 발송 차단. read-only.
 */
@Controller
@RequestMapping("/admin/system/member/dormant-notice")
public class DormantNoticeMngController {

    private static final Logger log = LoggerFactory.getLogger(DormantNoticeMngController.class);

    private final DormantAdminService    service;
    private final AuditLogger            auditLogger;
    private final ExcelResponseWriter    excelWriter;

    public DormantNoticeMngController(DormantAdminService service,
                                       AuditLogger auditLogger,
                                       ExcelResponseWriter excelWriter) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    @GetMapping
    public String list(@ModelAttribute("search") DormantNoticeSearch search, Model model) {
        List<DormantNotice> rows = service.searchNotice(search);
        int total = service.countNotice(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/member/dormant-notice/list";
    }

    @GetMapping("/{noticeId}")
    public String detail(@PathVariable String noticeId, Model model, RedirectAttributes ra) {
        DormantNotice row = service.getNotice(noticeId);
        if (row == null) {
            ra.addFlashAttribute("error", "발송 이력을 찾을 수 없습니다.");
            return "redirect:/admin/system/member/dormant-notice";
        }
        model.addAttribute("row", row);
        return "admin/system/member/dormant-notice/detail";
    }

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") DormantNoticeSearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/member/dormant-notice";
        }
        List<DormantNotice> rows = service.findNoticeForExport(search);

        String filename = excelWriter.write(res, "휴면안내발송이력", "gopcms_dormant_notice",
            new String[]{ "이력ID", "회원ID", "단계", "발송일시", "발송자IP" },
            rows,
            (row, n) -> {
                row.createCell(0).setCellValue(nvl(n.getNoticeId()));
                row.createCell(1).setCellValue(nvl(n.getMemberId()));
                row.createCell(2).setCellValue(nvl(n.getStage()));
                row.createCell(3).setCellValue(str(n.getSentAt()));
                row.createCell(4).setCellValue(nvl(n.getCreatedIp()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_member_dormant_notice")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size() + ",\"filename\":" + JsonUtils.quote(filename)
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===DORMANT_NOTICE_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }
}
