package com.gonet.primary.board.article.controller;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.ExcelDownloadRequest;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.excel.ExcelResponseWriter;
import com.gonet.common.util.JsonUtils;
import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.dto.BbsArticleSearch;
import com.gonet.primary.board.article.dto.BbsArticleStatus;
import com.gonet.primary.board.article.service.BoardArticleService;
import com.gonet.primary.board.category.service.BoardCategoryService;
import com.gonet.primary.board.comment.dto.BbsCommentStatus;
import com.gonet.primary.board.comment.service.BoardCommentService;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.service.BoardMasterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

import static com.gonet.common.excel.ExcelResponseWriter.nvl;
import static com.gonet.common.excel.ExcelResponseWriter.str;

/**
 * 게시글 관리자 모더레이션 Controller.
 *
 * <p>URL prefix: {@code /admin/system/board/{bbsMasterId}/article}.
 * <p>기능: 게시판별 글 목록/상세 + 강제 상태 전환(HIDDEN/REPORTED/PUBLISHED) + 공지 토글
 * + soft delete + 엑셀 다운로드.
 */
@Controller
@Validated
@RequestMapping("/admin/system/board/{bbsMasterId}/article")
public class BoardArticleMngController {

    private static final Logger log = LoggerFactory.getLogger(BoardArticleMngController.class);

    private final BoardArticleService  articleService;
    private final BoardCommentService  commentService;
    private final BoardCategoryService categoryService;
    private final BoardMasterService   masterService;
    private final AuditLogger          auditLogger;
    private final ExcelResponseWriter  excelWriter;

    public BoardArticleMngController(BoardArticleService articleService,
                                      BoardCommentService commentService,
                                      BoardCategoryService categoryService,
                                      BoardMasterService masterService,
                                      AuditLogger auditLogger,
                                      ExcelResponseWriter excelWriter) {
        this.articleService  = articleService;
        this.commentService  = commentService;
        this.categoryService = categoryService;
        this.masterService   = masterService;
        this.auditLogger     = auditLogger;
        this.excelWriter     = excelWriter;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    @GetMapping
    public String list(@PathVariable String bbsMasterId,
                        @ModelAttribute("search") BbsArticleSearch search,
                        Model model,
                        RedirectAttributes ra) {
        BbsMaster master = masterService.get(bbsMasterId);
        if (master == null) {
            ra.addFlashAttribute("error", "게시판을 찾을 수 없습니다.");
            return "redirect:/admin/system/board";
        }
        search.setBbsMasterId(bbsMasterId);

        // 통합 게시판 모드. 구분자 콤마
        if(master.isAggregator() && master.getGroupedBoardIds() != null && master.getGroupedBoardIds().contains(",")) {
            String[] groupedBoardIds = master.getGroupedBoardIds().split(",");
            search.setGroupedBoardIdArray(groupedBoardIds);
        }

        List<BbsArticle> rows = articleService.search(search);
        int total = articleService.count(search);

        model.addAttribute("master", master);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("statuses", BbsArticleStatus.values());
        model.addAttribute("categories", categoryService.listActive(bbsMasterId));
        return "admin/system/board/article/list";
    }

    @GetMapping("/{articleId}")
    public String detail(@PathVariable String bbsMasterId,
                          @PathVariable String articleId,
                          Model model,
                          RedirectAttributes ra) {
        BbsMaster master = masterService.get(bbsMasterId);
        BbsArticle article = articleService.getById(articleId);
        if (master == null || article == null) {
            ra.addFlashAttribute("error", "게시글을 찾을 수 없습니다.");
            return "redirect:/admin/system/board/" + bbsMasterId + "/article";
        }
        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("statuses", BbsArticleStatus.values());
        model.addAttribute("existingFiles",
            articleService.listAttachments(article.getFileGroupId()));
        model.addAttribute("comments", commentService.listByArticle(articleId));
        model.addAttribute("commentStatuses", BbsCommentStatus.values());
        return "admin/system/board/article/detail";
    }

    // ==================================================================
    // 모더레이션
    // ==================================================================

    @PostMapping("/{articleId}/status")
    public String changeStatus(@PathVariable String bbsMasterId,
                                @PathVariable String articleId,
                                @RequestParam("target")
                                @Pattern(regexp = "PUBLISHED|HIDDEN|REPORTED|UNPUBLISHED",
                                         message = "target 은 PUBLISHED/HIDDEN/REPORTED/UNPUBLISHED 중 하나")
                                String target,
                                RedirectAttributes ra,
                                HttpServletResponse res) {
        String redirect = "/admin/system/board/" + bbsMasterId + "/article/" + articleId;
        try {
            articleService.adminUpdateStatus(articleId, target);
            ra.addFlashAttribute("message", "상태를 변경했습니다: " + target);
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (jakarta.validation.ConstraintViolationException ex) {
            // M-11 — @Validated + @Pattern 위반 시. 정확한 메시지 노출.
            log.warn("BBS_ARTICLE_STATUS bad-target id={} target={} reason={}",
                articleId, target, ex.getMessage());
            ra.addFlashAttribute("error", "허용되지 않은 상태값입니다: " + target);
            return "redirect:" + redirect;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_ARTICLE_STATUS fail id={} target={} reason={}",
                articleId, target, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("BBS_ARTICLE_STATUS error id={}", articleId, ex);
            ra.addFlashAttribute("error", "상태 변경 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{articleId}/notice")
    public String toggleNotice(@PathVariable String bbsMasterId,
                                 @PathVariable String articleId,
                                 @RequestParam("notice") boolean notice,
                                 RedirectAttributes ra,
                                 HttpServletResponse res) {
        String redirect = "/admin/system/board/" + bbsMasterId + "/article/" + articleId;
        try {
            articleService.adminToggleNotice(articleId, notice);
            ra.addFlashAttribute("message", notice ? "공지로 지정했습니다." : "공지에서 해제했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (Exception ex) {
            log.warn("BBS_ARTICLE_NOTICE error id={}", articleId, ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
            return "redirect:" + redirect;
        }
    }

    @DeleteMapping("/{articleId}")
    public String delete(@PathVariable String bbsMasterId,
                          @PathVariable String articleId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        String redirectList = "/admin/system/board/" + bbsMasterId + "/article";
        try {
            articleService.softDelete(articleId);
            log.info("===BBS_ARTICLE_DELETE ok id={}", articleId);
            ra.addFlashAttribute("message", "게시글을 삭제했습니다.");
            res.setHeader("HX-Redirect", redirectList);
            return "redirect:" + redirectList;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_ARTICLE_DELETE fail id={} reason={}", articleId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirectList + "/" + articleId;
        } catch (Exception ex) {
            log.warn("BBS_ARTICLE_DELETE error id={}", articleId, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:" + redirectList + "/" + articleId;
        }
    }

    // ==================================================================
    // Excel
    // ==================================================================

    @PostMapping("/excel")
    public String excel(@PathVariable String bbsMasterId,
                         @Valid @ModelAttribute("excelReq") ExcelDownloadRequest excelReq,
                         BindingResult br,
                         @ModelAttribute("search") BbsArticleSearch search,
                         HttpServletRequest req,
                         HttpServletResponse res,
                         RedirectAttributes ra) throws IOException {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", "다운로드 사유는 10자 이상 입력해주세요.");
            return "redirect:/admin/system/board/" + bbsMasterId + "/article";
        }
        search.setBbsMasterId(bbsMasterId);
        search.setPage(1);
        search.setPageSize(10_000);
        List<BbsArticle> rows = articleService.search(search);

        String excelFilename = excelWriter.write(res, "게시글", "gopcms_articles",
            new String[]{ "ID", "게시판", "제목", "작성자", "공지", "비밀",
                          "조회", "댓글", "상태", "작성시각", "수정시각" },
            rows,
            (row, a) -> {
                row.createCell(0).setCellValue(nvl(a.getArticleId()));
                row.createCell(1).setCellValue(nvl(a.getBbsCode()));
                row.createCell(2).setCellValue(nvl(a.getTitle()));
                row.createCell(3).setCellValue(nvl(a.getWriterName()));
                row.createCell(4).setCellValue(nvl(a.getNoticeYn()));
                row.createCell(5).setCellValue(nvl(a.getSecretYn()));
                row.createCell(6).setCellValue(a.getViewCount());
                row.createCell(7).setCellValue(a.getCommentCount());
                row.createCell(8).setCellValue(nvl(a.getStatus()));
                row.createCell(9).setCellValue(str(a.getCreatedAt()));
                row.createCell(10).setCellValue(str(a.getUpdatedAt()));
            });

        auditLogger.write(AuditEvent.of("EXCEL_DOWNLOAD", "tb_bbs_article")
            .withHttp(req.getMethod(), req.getRequestURI(),
                      req.getRemoteAddr(), req.getHeader("User-Agent"))
            .withResult("SUCCESS")
            .withAfter("{\"bbsMasterId\":" + JsonUtils.quote(bbsMasterId)
                + ",\"count\":" + rows.size()
                + ",\"reason\":" + JsonUtils.quote(excelReq.getDownloadReason()) + "}"));
        log.info("===BBS_ARTICLE_EXCEL ok bbs={} count={}", bbsMasterId, rows.size());
        return null;
    }
}
