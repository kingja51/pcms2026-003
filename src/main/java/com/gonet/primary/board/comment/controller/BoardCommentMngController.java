package com.gonet.primary.board.comment.controller;

import com.gonet.primary.board.comment.service.BoardCommentService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리자 댓글 모더레이션.
 *
 * <p>URL prefix: {@code /admin/system/board/{bbsMasterId}/article/{articleId}/comments/{commentId}}.
 * 응답은 항상 article 상세로 redirect.
 */
@Controller
@RequestMapping("/admin/system/board/{bbsMasterId}/article/{articleId}/comments/{commentId}")
public class BoardCommentMngController {

    private static final Logger log = LoggerFactory.getLogger(BoardCommentMngController.class);

    private final BoardCommentService service;

    public BoardCommentMngController(BoardCommentService service) {
        this.service = service;
    }

    @PostMapping("/status")
    public String changeStatus(@PathVariable String bbsMasterId,
                                @PathVariable String articleId,
                                @PathVariable String commentId,
                                @RequestParam("target") String target,
                                RedirectAttributes ra,
                                HttpServletResponse res) {
        String redirect = "/admin/system/board/" + bbsMasterId + "/article/" + articleId;
        try {
            service.adminUpdateStatus(commentId, target);
            ra.addFlashAttribute("message", "댓글 상태를 변경했습니다: " + target);
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_COMMENT_STATUS fail id={} target={} reason={}",
                commentId, target, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirect;
        }
    }

    @DeleteMapping
    public String delete(@PathVariable String bbsMasterId,
                          @PathVariable String articleId,
                          @PathVariable String commentId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        String redirect = "/admin/system/board/" + bbsMasterId + "/article/" + articleId;
        try {
            service.softDelete(commentId);
            ra.addFlashAttribute("message", "댓글을 삭제했습니다.");
            res.setHeader("HX-Redirect", redirect);
            return "redirect:" + redirect;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_COMMENT_DELETE fail id={} reason={}", commentId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + redirect;
        }
    }
}
