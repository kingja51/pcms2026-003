package com.gonet.primary.board.comment.controller;

import com.gonet.common.util.IpUtils;
import com.gonet.primary.board.comment.dto.BbsCommentSaveForm;
import com.gonet.primary.board.comment.service.BoardCommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 사용자 댓글 작성/수정/삭제.
 *
 * <p>URL prefix: {@code /bbs/{siteCode}/{bbsCode}/{articleId}/comments}.
 * 응답은 항상 게시글 상세로 redirect.
 */
@Controller
@RequestMapping("/bbs/{siteCode}/{bbsCode}/{articleId}/comments")
public class BoardCommentUsrController {

    private static final Logger log = LoggerFactory.getLogger(BoardCommentUsrController.class);

    private final BoardCommentService service;

    public BoardCommentUsrController(BoardCommentService service) {
        this.service = service;
    }

    @PostMapping
    public String create(@PathVariable String siteCode,
                          @PathVariable String bbsCode,
                          @PathVariable String articleId,
                          @Valid @ModelAttribute("commentForm") BbsCommentSaveForm form,
                          BindingResult br,
                          HttpServletRequest req,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setArticleId(articleId);
        String redirect = "/bbs/" + siteCode + "/" + bbsCode + "/" + articleId;

        if (br.hasErrors()) {
            ra.addFlashAttribute("commentError",
                br.getFieldError() != null ? br.getFieldError().getDefaultMessage() : "댓글이 올바르지 않습니다.");
            return "redirect:" + redirect;
        }
        try {
            service.create(form, IpUtils.clientIp(req));
            res.setHeader("HX-Redirect", redirect);
            ra.addFlashAttribute("message", "댓글을 등록했습니다.");
            return "redirect:" + redirect;
        } catch (AccessDeniedException ex) {
            log.info("===BBS_COMMENT_CREATE denied article={} reason={}", articleId, ex.getMessage());
            throw ex;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("BBS_COMMENT_CREATE fail article={} reason={}", articleId, ex.getMessage());
            ra.addFlashAttribute("commentError", ex.getMessage());
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{commentId}")
    public String update(@PathVariable String siteCode,
                          @PathVariable String bbsCode,
                          @PathVariable String articleId,
                          @PathVariable String commentId,
                          @Valid @ModelAttribute("commentForm") BbsCommentSaveForm form,
                          BindingResult br,
                          HttpServletRequest req,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        form.setArticleId(articleId);
        form.setCommentId(commentId);
        String redirect = "/bbs/" + siteCode + "/" + bbsCode + "/" + articleId;

        if (br.hasErrors()) {
            ra.addFlashAttribute("commentError",
                br.getFieldError() != null ? br.getFieldError().getDefaultMessage() : "댓글이 올바르지 않습니다.");
            return "redirect:" + redirect;
        }
        try {
            service.update(form, IpUtils.clientIp(req));
            res.setHeader("HX-Redirect", redirect);
            ra.addFlashAttribute("message", "댓글을 수정했습니다.");
            return "redirect:" + redirect;
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_COMMENT_UPDATE fail id={} reason={}", commentId, ex.getMessage());
            ra.addFlashAttribute("commentError", ex.getMessage());
            return "redirect:" + redirect;
        }
    }

    @PostMapping("/{commentId}/delete")
    public String delete(@PathVariable String siteCode,
                          @PathVariable String bbsCode,
                          @PathVariable String articleId,
                          @PathVariable String commentId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        String redirect = "/bbs/" + siteCode + "/" + bbsCode + "/" + articleId;
        try {
            service.softDelete(commentId);
            res.setHeader("HX-Redirect", redirect);
            ra.addFlashAttribute("message", "댓글을 삭제했습니다.");
            return "redirect:" + redirect;
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_COMMENT_DELETE fail id={} reason={}", commentId, ex.getMessage());
            ra.addFlashAttribute("commentError", ex.getMessage());
            return "redirect:" + redirect;
        }
    }
}
