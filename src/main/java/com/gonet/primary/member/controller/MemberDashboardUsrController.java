package com.gonet.primary.member.controller;

import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.service.BoardArticleService;
import com.gonet.primary.board.comment.dto.BbsComment;
import com.gonet.primary.board.comment.service.BoardCommentService;
import com.gonet.primary.board.report.dto.BbsReport;
import com.gonet.primary.board.report.service.BoardReportService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.site.service.SiteContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 회원 대시보드 — {@code GET /member/dashboard}.
 *
 * <p>본인이 참여한 활동 요약:
 * <ul>
 *   <li>내가 작성한 게시글 최신 5건 — created_by = userId</li>
 *   <li>내가 작성한 댓글 최신 5건   — created_by = userId</li>
 *   <li>내가 신고한 신고 모더레이션 최신 5건 — reporter_user_id = userId</li>
 * </ul>
 *
 * <p>I-013 운영 가이드 준수 — `/member/**` 평면 URL. siteContext 는
 * SiteContextResolver 가 session-sticky 또는 EMPTY 폴백으로 해석.
 *
 * <p>R1 ArchUnit 정합 — 3 도메인 Service 인터페이스만 주입. Mapper 직접 호출 금지.
 */
@Controller
public class MemberDashboardUsrController {

    private static final Logger log = LoggerFactory.getLogger(MemberDashboardUsrController.class);

    private static final int RECENT_LIMIT = 5;

    private final BoardArticleService articleService;
    private final BoardCommentService commentService;
    private final BoardReportService  reportService;
    private final SiteContextResolver siteContextResolver;

    public MemberDashboardUsrController(BoardArticleService articleService,
                                         BoardCommentService commentService,
                                         BoardReportService reportService,
                                         SiteContextResolver siteContextResolver) {
        this.articleService = articleService;
        this.commentService = commentService;
        this.reportService  = reportService;
        this.siteContextResolver = siteContextResolver;
    }

    @ModelAttribute
    public void injectSiteContext(HttpServletRequest req, Model model) {
        siteContextResolver.inject(req, model);
    }

    @GetMapping("/member/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal,
                             Model model, RedirectAttributes ra) {
        if (principal == null || principal.getUserId() == null) {
            ra.addFlashAttribute("error", "로그인이 필요합니다.");
            return "redirect:/member/login";
        }
        String userId = principal.getUserId();

        List<BbsArticle> myArticles = articleService.findRecentByCreatedBy(userId, RECENT_LIMIT);
        List<BbsComment> myComments = commentService.findRecentByCreatedBy(userId, RECENT_LIMIT);
        List<BbsReport>  myReports  = reportService.findRecentByReporter(userId, RECENT_LIMIT);

        model.addAttribute("myArticles", myArticles);
        model.addAttribute("myComments", myComments);
        model.addAttribute("myReports",  myReports);
        model.addAttribute("loginId",    principal.getUsername());

        log.info("MEMBER_DASHBOARD ok userId={} articles={} comments={} reports={}",
            userId, myArticles.size(), myComments.size(), myReports.size());
        return "front/member-dashboard";
    }
}
