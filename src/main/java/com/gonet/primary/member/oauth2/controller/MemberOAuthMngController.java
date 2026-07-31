package com.gonet.primary.member.oauth2.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.member.oauth2.dto.MemberOAuth;
import com.gonet.primary.member.oauth2.dto.MemberOAuthSearch;
import com.gonet.primary.member.oauth2.service.MemberOAuthMngService;
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
 * 회원 OAuth2 매핑 — {@code /admin/system/member/oauth}.
 *
 * <p>provider × providerUserId UNIQUE 매핑. emailAtLink / nameAtLink 는 연결 당시 외부
 * 제공자가 알려준 값으로 감사용 평문 보관 (PII 다소). read-only.
 */
@Controller
@RequestMapping("/admin/system/member/oauth")
public class MemberOAuthMngController {

    private static final Logger log = LoggerFactory.getLogger(MemberOAuthMngController.class);

    private final MemberOAuthMngService service;
    private final AuditLogger           auditLogger;
    private final ExcelResponseWriter   excelWriter;

    public MemberOAuthMngController(MemberOAuthMngService service,
                                     AuditLogger auditLogger,
                                     ExcelResponseWriter excelWriter) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
    }

    @GetMapping
    public String list(@ModelAttribute("search") MemberOAuthSearch search, Model model) {
        List<MemberOAuth> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        return "admin/system/member/oauth/list";
    }

    @GetMapping("/{memberOauthId}")
    public String detail(@PathVariable String memberOauthId, Model model, RedirectAttributes ra) {
        MemberOAuth row = service.get(memberOauthId);
        if (row == null) {
            ra.addFlashAttribute("error", "OAuth 매핑을 찾을 수 없습니다.");
            return "redirect:/admin/system/member/oauth";
        }
        model.addAttribute("row", row);
        return "admin/system/member/oauth/detail";
    }

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") MemberOAuthSearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/member/oauth";
        }
        List<MemberOAuth> rows = service.findForExport(search);

        String filename = excelWriter.write(res, "회원OAuth매핑", "gopcms_member_oauth",
            new String[]{ "매핑ID", "회원ID", "Provider", "ProviderUserId",
                          "연결당시이메일", "연결당시이름", "연결일시", "직전로그인" },
            rows,
            (row, o) -> {
                row.createCell(0).setCellValue(nvl(o.getMemberOauthId()));
                row.createCell(1).setCellValue(nvl(o.getMemberId()));
                row.createCell(2).setCellValue(nvl(o.getProvider()));
                row.createCell(3).setCellValue(nvl(o.getProviderUserId()));
                row.createCell(4).setCellValue(nvl(o.getEmailAtLink()));
                row.createCell(5).setCellValue(nvl(o.getNameAtLink()));
                row.createCell(6).setCellValue(str(o.getLinkedAt()));
                row.createCell(7).setCellValue(str(o.getLastLoginAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_member_oauth")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size() + ",\"filename\":" + JsonUtils.quote(filename)
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===MEMBER_OAUTH_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }
}
