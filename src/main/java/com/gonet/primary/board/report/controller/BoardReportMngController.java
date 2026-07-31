package com.gonet.primary.board.report.controller;

import com.gonet.primary.board.report.dto.BbsReport;
import com.gonet.primary.board.report.dto.BbsReportSearch;
import com.gonet.primary.board.report.service.BoardReportService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 신고 모더레이션 관리 Controller — list / detail / review.
 *
 * <p>URL prefix: {@code /admin/system/board/report}.
 *
 * <p>관리자가 신고된 article/comment 를 검토하고 REVIEWED/REJECTED 로 처리.
 * 자동 REPORTED 전환과는 독립 — 관리자는 콘텐츠 status 를 별도로 PUBLISHED/HIDDEN 등으로
 * 조정해야 함 (기존 BoardArticleMngController/BoardCommentMngController).
 */
@Controller
@RequestMapping("/admin/system/board/report")
public class BoardReportMngController {

    private static final Logger log = LoggerFactory.getLogger(BoardReportMngController.class);

    private final BoardReportService service;

    public BoardReportMngController(BoardReportService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@ModelAttribute("search") BbsReportSearch search, Model model) {
        model.addAttribute("page", service.search(search));
        return "admin/system/board/report/list";
    }

    @GetMapping("/{reportId}")
    public String detail(@PathVariable String reportId,
                          Model model,
                          RedirectAttributes ra) {
        BbsReport r = service.getById(reportId);
        if (r == null) {
            ra.addFlashAttribute("error", "신고를 찾을 수 없습니다.");
            return "redirect:/admin/system/board/report";
        }
        model.addAttribute("report", r);
        return "admin/system/board/report/detail";
    }

    @PostMapping("/{reportId}/review")
    public String review(@PathVariable String reportId,
                          @RequestParam String status,
                          @RequestParam(required = false) String reviewNote,
                          @AuthenticationPrincipal CustomUserDetails me,
                          HttpServletResponse res,
                          RedirectAttributes ra) {
        try {
            service.review(reportId, status, reviewNote, me);
            ra.addFlashAttribute("message",
                "REVIEWED".equals(status) ? "검토 완료로 처리했습니다." : "기각으로 처리했습니다.");
            res.setHeader("HX-Redirect", "/admin/system/board/report/" + reportId);
            return "redirect:/admin/system/board/report/" + reportId;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_REPORT_REVIEW_FAIL id={} reason={}", reportId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/board/report/" + reportId;
        } catch (Exception ex) {
            log.warn("BBS_REPORT_REVIEW_ERROR id={}", reportId, ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:/admin/system/board/report/" + reportId;
        }
    }
}
