package com.gonet.primary.board.like.controller;

import com.gonet.common.dto.ApiResponse;
import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.like.dto.LikeToggleResult;
import com.gonet.primary.board.like.service.BoardLikeService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 좋아요 토글 REST API.
 *
 * <p>URL:
 * <ul>
 *   <li>{@code POST /api/v1/board/like/article/{articleId}/toggle}</li>
 *   <li>{@code POST /api/v1/board/like/comment/{commentId}/toggle}</li>
 * </ul>
 *
 * <p>요청 form 파라미터 {@code sourceUrl} (선택) — 좋아요가 클릭된 페이지 URL.
 * 신규 INSERT 시에만 저장 (이후 토글 ON/OFF 갱신 안 함).
 *
 * <p>인증 필수 (URL access 규칙 a-board-like-toggle: AUTHENTICATED).
 * 비로그인 호출 시 Spring Security 가 EntryPoint 로 redirect — 본 컨트롤러까지 도달하지 않음.
 */
@Tag(name = "Board Like",
    description = "게시판/콘텐츠 좋아요 멱등 토글. UNIQUE(target_type, target_id, user_id) DB 강제로 " +
        "중복 INSERT 차단 — 같은 사용자가 다시 호출하면 OFF, 또 호출하면 ON 으로 전환. " +
        "토글 시 like_count 비정규화 컬럼 동기화. 인증 필수 (URL access: AUTHENTICATED).")
@RestController
@RequestMapping("/api/v1/board/like")
public class BoardLikeApiController {

    private static final Logger log = LoggerFactory.getLogger(BoardLikeApiController.class);

    private final BoardLikeService service;

    public BoardLikeApiController(BoardLikeService service) {
        this.service = service;
    }

    @Operation(summary = "게시글 좋아요 토글", description = "tb_bbs_article 행에 대한 좋아요 ON/OFF.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토글 성공 — 응답 data.liked 로 현재 상태"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 위반 (articleId 형식 / 미존재)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "비로그인"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 — 비공개 게시판 등")
    })
    @PostMapping("/article/{articleId}/toggle")
    public ResponseEntity<ApiResponse<LikeToggleResult>> toggleArticle(
            @Parameter(description = "게시글 ID (UUID v7)", required = true)
            @PathVariable String articleId,
            @Parameter(description = "신규 INSERT 시 저장되는 출처 페이지 URL (선택)")
            @RequestParam(name = "sourceUrl", required = false) String sourceUrl,
            @AuthenticationPrincipal CustomUserDetails me) {
        return doToggle(BbsLikeTarget.ARTICLE, articleId, sourceUrl, me);
    }

    @Operation(summary = "댓글 좋아요 토글", description = "tb_bbs_comment 행에 대한 좋아요 ON/OFF.")
    @PostMapping("/comment/{commentId}/toggle")
    public ResponseEntity<ApiResponse<LikeToggleResult>> toggleComment(
            @Parameter(description = "댓글 ID (UUID v7)", required = true)
            @PathVariable String commentId,
            @RequestParam(name = "sourceUrl", required = false) String sourceUrl,
            @AuthenticationPrincipal CustomUserDetails me) {
        return doToggle(BbsLikeTarget.COMMENT, commentId, sourceUrl, me);
    }

    @Operation(summary = "콘텐츠 좋아요 토글", description = "tb_content 행에 대한 좋아요 ON/OFF.")
    @PostMapping("/content/{contentId}/toggle")
    public ResponseEntity<ApiResponse<LikeToggleResult>> toggleContent(
            @Parameter(description = "콘텐츠 ID (UUID v7)", required = true)
            @PathVariable String contentId,
            @RequestParam(name = "sourceUrl", required = false) String sourceUrl,
            @AuthenticationPrincipal CustomUserDetails me) {
        return doToggle(BbsLikeTarget.CONTENT, contentId, sourceUrl, me);
    }

    private ResponseEntity<ApiResponse<LikeToggleResult>> doToggle(
            BbsLikeTarget target, String id, String sourceUrl, CustomUserDetails me) {
        try {
            LikeToggleResult r = service.toggle(target, id, sourceUrl, me);
            return ResponseEntity.ok(ApiResponse.ok(r));
        } catch (AccessDeniedException ex) {
            log.info("===BBS_LIKE_DENIED target={}/{} reason={}", target, id, ex.getMessage());
            return ResponseEntity.status(403).body(ApiResponse.fail(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            log.info("===BBS_LIKE_FAIL target={}/{} reason={}", target, id, ex.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
        } catch (DataAccessException ex) {
            // H-4 — DB/연결 장애 분리. 503 으로 클라이언트가 재시도 결정 가능.
            log.warn("BBS_LIKE_DB_ERROR target={}/{}", target, id, ex);
            return ResponseEntity.status(503).body(
                ApiResponse.fail("일시적으로 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."));
        } catch (RuntimeException ex) {
            log.warn("BBS_LIKE_ERROR target={}/{}", target, id, ex);
            return ResponseEntity.status(500).body(ApiResponse.fail("처리 중 오류가 발생했습니다."));
        }
    }
}
