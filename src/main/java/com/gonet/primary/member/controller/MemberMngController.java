package com.gonet.primary.member.controller;

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
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.dto.MemberSearch;
import com.gonet.primary.member.service.MemberService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

import static com.gonet.common.excel.ExcelResponseWriter.nvl;
import static com.gonet.common.excel.ExcelResponseWriter.str;

/**
 * 관리자용 회원 관리 — {@code /admin/system/member}.
 *
 * <p>관리자는 회원의 상태(ACTIVE/LOCKED/SUSPENDED) 를 변경하거나 강제 탈퇴시킬 수 있다.
 * 개인정보 수정은 본인만 가능 (마이페이지). 엑셀 다운로드는 사유 필수 + 감사로그.
 */
@Controller
@RequestMapping("/admin/system/member")
public class MemberMngController {

    private static final Logger log = LoggerFactory.getLogger(MemberMngController.class);

    private final MemberService          service;
    private final AuditLogger            auditLogger;
    private final ExcelResponseWriter    excelWriter;
    private final PrivacyAccessLogger    privacyAccessLogger;

    public MemberMngController(MemberService service,
                                AuditLogger auditLogger,
                                ExcelResponseWriter excelWriter,
                                PrivacyAccessLogger privacyAccessLogger) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.excelWriter = excelWriter;
        this.privacyAccessLogger = privacyAccessLogger;
    }

    @GetMapping
    public String list(@ModelAttribute("search") MemberSearch search, Model model) {
        List<Member> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        privacyAccessLogger.write(
            PrivacyAccessEvent.of(PrivacyAccessLog.ACTION_SEARCH, "tb_member")
                .withCount(rows.size())
                .withFields("name,email,phone")
        );
        return "admin/system/member/list";
    }

    @GetMapping("/{memberId}")
    public String detail(@PathVariable String memberId, Model model, RedirectAttributes ra) {
        Member m = service.get(memberId);
        if (m == null) {
            ra.addFlashAttribute("error", "회원을 찾을 수 없습니다.");
            return "redirect:/admin/system/member";
        }
        model.addAttribute("member", m);
        model.addAttribute("consents", service.listConsents(memberId));
        privacyAccessLogger.write(
            PrivacyAccessEvent.of(PrivacyAccessLog.ACTION_READ, "tb_member")
                .withTarget("MEMBER", memberId)
                .withFields("name,email,phone")
        );
        return "admin/system/member/detail";
    }

    @PostMapping("/{memberId}/status")
    public String changeStatus(@PathVariable String memberId,
                                @RequestParam String status,
                                RedirectAttributes ra, HttpServletResponse res) {
        try {
            service.adminUpdateStatus(memberId, status);
            log.info("===MEMBER_STATUS_CHANGE ok memberId={} status={}", memberId, status);
            ra.addFlashAttribute("message", "회원 상태를 변경했습니다: " + status);
            res.setHeader("HX-Redirect", "/admin/system/member/" + memberId);
            return "redirect:/admin/system/member/" + memberId;
        } catch (IllegalArgumentException ex) {
            log.warn("MEMBER_STATUS_CHANGE fail memberId={} reason={}", memberId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/member/" + memberId;
        } catch (Exception ex) {
            log.warn("MEMBER_STATUS_CHANGE error memberId={} reason={}", memberId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "상태 변경 중 오류가 발생했습니다.");
            return "redirect:/admin/system/member/" + memberId;
        }
    }

    @PostMapping("/{memberId}/reset-password")
    public String resetPassword(@PathVariable String memberId,
                                 RedirectAttributes ra, HttpServletResponse res) {
        try {
            service.resetPasswordAndSendMail(memberId);
            log.info("===MEMBER_PWD_RESET ok memberId={}", memberId);   // 평문 비밀번호는 기록 금지
            ra.addFlashAttribute("message", "임시 비밀번호를 회원 이메일로 발송했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/member/" + memberId);
            return "redirect:/admin/system/member/" + memberId;
        } catch (IllegalArgumentException ex) {
            log.warn("MEMBER_PWD_RESET fail memberId={} reason={}", memberId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/member/" + memberId;
        } catch (IllegalStateException ex) {
            log.warn("MEMBER_PWD_RESET mail-fail memberId={} reason={}", memberId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/member/" + memberId;
        } catch (Exception ex) {
            log.warn("MEMBER_PWD_RESET error memberId={} reason={}", memberId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "비밀번호 초기화 중 오류가 발생했습니다.");
            return "redirect:/admin/system/member/" + memberId;
        }
    }

    @PostMapping("/{memberId}/unlock")
    public String unlock(@PathVariable String memberId,
                          RedirectAttributes ra, HttpServletResponse res) {
        try {
            service.adminUnlock(memberId);
            log.info("===MEMBER_UNLOCK ok memberId={}", memberId);
            ra.addFlashAttribute("message", "회원 계정 잠금을 해제했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/member/" + memberId);
            return "redirect:/admin/system/member/" + memberId;
        } catch (IllegalArgumentException ex) {
            log.warn("MEMBER_UNLOCK fail memberId={} reason={}", memberId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/member/" + memberId;
        } catch (Exception ex) {
            log.warn("MEMBER_UNLOCK error memberId={} reason={}", memberId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "잠금 해제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/member/" + memberId;
        }
    }

    /**
     * 관리자 강제 탈퇴 — 사유(reason) 자유 텍스트 5자 이상 필수.
     * 본 메서드는 사유 길이 사전 검증만 수행. 비즈니스 검증은 Service 단에서 재검증.
     */
    @DeleteMapping("/{memberId}")
    public String delete(@PathVariable String memberId,
                          @RequestParam("reason") String reason,
                          RedirectAttributes ra, HttpServletResponse res) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 5) {
            ra.addFlashAttribute("error", "강제 탈퇴 사유는 5자 이상 입력해 주세요.");
            return "redirect:/admin/system/member/" + memberId;
        }
        try {
            service.adminSoftDelete(memberId, trimmed);
            // 평문 사유는 운영 로그에 1줄 — log_audit.after_value 에는 Service 가 별도 기록
            log.info("===MEMBER_FORCE_DELETE ok memberId={} reasonLen={}", memberId, trimmed.length());
            ra.addFlashAttribute("message", "회원을 탈퇴 처리했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/member");
            return "redirect:/admin/system/member";
        } catch (IllegalArgumentException ex) {
            log.warn("MEMBER_FORCE_DELETE fail memberId={} reason={}", memberId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/member/" + memberId;
        } catch (Exception ex) {
            log.warn("MEMBER_FORCE_DELETE error memberId={} reason={}", memberId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "탈퇴 처리 중 오류가 발생했습니다.");
            return "redirect:/admin/system/member/" + memberId;
        }
    }

    // ==================================================================
    // Excel
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") MemberSearch search,
                         HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/member";
        }
        search.setPage(1);
        search.setPageSizeUnbounded(1000);
        List<Member> rows = service.search(search);

        String excelFilename = excelWriter.write(res, "회원", "gopcms_members",
            new String[]{ "회원ID", "사이트", "로그인ID", "이름(마스킹)", "이메일(마스킹)",
                          "전화(마스킹)", "상태", "가입유형", "최종로그인", "등록일시" },
            rows,
            (row, m) -> {
                row.createCell(0).setCellValue(nvl(m.getMemberId()));
                row.createCell(1).setCellValue(nvl(m.getSiteCode()));
                row.createCell(2).setCellValue(nvl(m.getLoginId()));
                row.createCell(3).setCellValue(nvl(MaskUtils.name(m.getMemberName())));
                row.createCell(4).setCellValue(nvl(MaskUtils.email(m.getEmail())));
                row.createCell(5).setCellValue(nvl(MaskUtils.phone(m.getPhone())));
                row.createCell(6).setCellValue(nvl(m.getStatus()));
                row.createCell(7).setCellValue(nvl(m.getJoinType()));
                row.createCell(8).setCellValue(str(m.getLastLoginAt()));
                row.createCell(9).setCellValue(str(m.getCreatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_member")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"count\":" + rows.size()
                + ",\"filename\":" + JsonUtils.quote(excelFilename)
                + ",\"keyword\":" + JsonUtils.quote(search.getKeyword())
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        // PIPA — log_privacy_access (24개월 보존, 사유 필수)
        privacyAccessLogger.write(
            PrivacyAccessEvent.of(PrivacyAccessLog.ACTION_EXPORT, "tb_member")
                .withCount(rows.size())
                .withFields("name,email,phone")
                .withReason(excelReq.getDownloadReason())
        );
        log.info("===MEMBER_EXCEL ok count={} reason='{}'", rows.size(), excelReq.getDownloadReason());
        return null;
    }
}
