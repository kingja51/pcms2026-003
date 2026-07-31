package com.gonet.primary.board.report.controller;

import com.gonet.common.dto.ApiResponse;
import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.report.dto.BbsReportSaveForm;
import com.gonet.primary.board.report.service.BoardReportService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 신고 REST API.
 *
 * <p>URL:
 * <ul>
 *   <li>{@code POST /api/v1/board/report/article/{articleId}}</li>
 *   <li>{@code POST /api/v1/board/report/comment/{commentId}}</li>
 * </ul>
 *
 * <p>요청 바디:
 * <pre>
 *   { "reasonCode": "SPAM|OFFENSIVE|...", "reasonText": "추가 설명" }
 * </pre>
 *
 * <p>인증 필수 — URL access 규칙 a-board-report.
 * 같은 사용자가 같은 대상을 다시 신고하면 409 Conflict.
 */
@Tag(name = "Board Report",
    description = "게시판/콘텐츠 신고 적재 + 임계 누적 시 자동 PUBLISHED→REPORTED 전환 (§0.25). " +
        "같은 사용자가 같은 대상을 다시 신고하면 409 Conflict. 인증 필수.")
@RestController
@RequestMapping("/api/v1/board/report")
public class BoardReportApiController {

    private static final Logger log = LoggerFactory.getLogger(BoardReportApiController.class);

    private final BoardReportService service;

    public BoardReportApiController(BoardReportService service) {
        this.service = service;
    }

    @Operation(summary = "게시글 신고",
        description = "임계(`gopcms.board.report-threshold`, 기본 5) 도달 시 article.status 가 " +
            "PUBLISHED→REPORTED 자동 전환되어 모더레이션 큐로 이동.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 접수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사유 누락/형식 위반"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "비로그인"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "같은 대상 중복 신고")
    })
    @PostMapping("/article/{articleId}")
    public ResponseEntity<ApiResponse<Void>> reportArticle(
            @Parameter(description = "게시글 ID (UUID v7)", required = true)
            @PathVariable String articleId,
            @Valid @RequestBody BbsReportSaveForm form, BindingResult br,
            @AuthenticationPrincipal CustomUserDetails me) {
        return doReport(BbsLikeTarget.ARTICLE, articleId, form, br, me);
    }

    @Operation(summary = "댓글 신고")
    @PostMapping("/comment/{commentId}")
    public ResponseEntity<ApiResponse<Void>> reportComment(
            @Parameter(description = "댓글 ID (UUID v7)", required = true)
            @PathVariable String commentId,
            @Valid @RequestBody BbsReportSaveForm form, BindingResult br,
            @AuthenticationPrincipal CustomUserDetails me) {
        return doReport(BbsLikeTarget.COMMENT, commentId, form, br, me);
    }

    @Operation(summary = "콘텐츠 신고")
    @PostMapping("/content/{contentId}")
    public ResponseEntity<ApiResponse<Void>> reportContent(
            @Parameter(description = "콘텐츠 ID (UUID v7)", required = true)
            @PathVariable String contentId,
            @Valid @RequestBody BbsReportSaveForm form, BindingResult br,
            @AuthenticationPrincipal CustomUserDetails me) {
        return doReport(BbsLikeTarget.CONTENT, contentId, form, br, me);
    }

    private ResponseEntity<ApiResponse<Void>> doReport(
            BbsLikeTarget target, String id,
            BbsReportSaveForm form, BindingResult br, CustomUserDetails me) {
        if (br.hasErrors()) {
            String firstError = br.getFieldError() != null
                ? br.getFieldError().getDefaultMessage()
                : "신고 사유가 올바르지 않습니다.";
            return ResponseEntity.badRequest().body(ApiResponse.fail(firstError));
        }
        try {
            service.report(target, id, form, me);
            return ResponseEntity.ok(ApiResponse.ok("신고가 접수되었습니다.", null));
        } catch (AccessDeniedException ex) {
            log.info("===BBS_REPORT_DENIED target={}/{} reason={}", target, id, ex.getMessage());
            return ResponseEntity.status(403).body(ApiResponse.fail(ex.getMessage()));
        } catch (IllegalStateException ex) {
            log.info("===BBS_REPORT_DUPLICATE target={}/{}", target, id);
            return ResponseEntity.status(409).body(ApiResponse.fail(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            log.info("===BBS_REPORT_FAIL target={}/{} reason={}", target, id, ex.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
        } catch (DataAccessException ex) {
            // H-4 — DB/연결 장애. 내부 message 비공개 + 일시적 장애 응답.
            log.warn("BBS_REPORT_DB_ERROR target={}/{}", target, id, ex);
            return ResponseEntity.status(503).body(
                ApiResponse.fail("일시적으로 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."));
        } catch (RuntimeException ex) {
            // 예상치 못한 런타임 — 스택트레이스만 서버 로그에, 사용자에게는 generic.
            log.warn("BBS_REPORT_ERROR target={}/{}", target, id, ex);
            return ResponseEntity.status(500).body(ApiResponse.fail("처리 중 오류가 발생했습니다."));
        }
    }
}
