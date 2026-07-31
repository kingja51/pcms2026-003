package com.gonet.primary.board.comment.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.mapper.BbsArticleMapper;
import com.gonet.primary.board.comment.dto.BbsComment;
import com.gonet.primary.board.comment.dto.BbsCommentSaveForm;
import com.gonet.primary.board.comment.dto.BbsCommentStatus;
import com.gonet.primary.board.comment.mapper.BbsCommentMapper;
import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.mapper.BbsMasterMapper;
import com.gonet.primary.board.report.service.BoardReportService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 댓글 서비스 구현 — eGov {@link EgovAbstractServiceImpl} 상속.
 *
 * <p>article.comment_count 는 PUBLISHED 댓글 수로 재계산 (recomputeCommentCount)
 * — 모든 CUD 후 호출.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class BoardCommentServiceImpl extends EgovAbstractServiceImpl implements BoardCommentService {

    private static final Logger log = LoggerFactory.getLogger(BoardCommentServiceImpl.class);

    private final BbsCommentMapper    commentMapper;
    private final BbsArticleMapper    articleMapper;
    private final BbsMasterMapper     masterMapper;
    private final AuditLogger         auditLogger;
    private final com.gonet.primary.notification.service.NotificationService notificationService;
    /** REPORTED → PUBLISHED 복귀 시 PENDING 신고 일괄 정리 (C-2 회귀 방지). */
    private final BoardReportService  reportService;

    public BoardCommentServiceImpl(BbsCommentMapper commentMapper,
                                    BbsArticleMapper articleMapper,
                                    BbsMasterMapper masterMapper,
                                    AuditLogger auditLogger,
                                    com.gonet.primary.notification.service.NotificationService notificationService,
                                    BoardReportService reportService) {
        this.commentMapper       = commentMapper;
        this.articleMapper       = articleMapper;
        this.masterMapper        = masterMapper;
        this.auditLogger         = auditLogger;
        this.notificationService = notificationService;
        this.reportService       = reportService;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override
    public List<BbsComment> listByArticle(String articleId) {
        if (articleId == null || articleId.isBlank()) return List.of();
        List<BbsComment> rows = commentMapper.findByArticleId(articleId);
        if (rows.isEmpty()) return rows;

        // 비밀 댓글 본문 마스킹 — 작성자/글주인/관리자 외에는 본문을 안내문으로 대체
        CustomUserDetails me = currentUser();
        BbsArticle article = articleMapper.findById(articleId);
        String articleOwnerId = (article == null) ? null : article.getWriterUserId();
        boolean staff = isStaffOrAbove(me);

        for (BbsComment c : rows) {
            if (!c.isSecret()) continue;
            if (staff) continue;
            if (isOwner(c, me)) continue;
            if (me != null && me.getUserId() != null
                && me.getUserId().equals(articleOwnerId)) continue;
            c.setContent("🔒 비밀 댓글입니다.");
        }
        return rows;
    }

    @Override
    public BbsComment get(String commentId) {
        if (commentId == null || commentId.isBlank()) return null;
        return commentMapper.findById(commentId);
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(BbsCommentSaveForm form, String clientIp) {
        Objects.requireNonNull(form, "form");
        CustomUserDetails me = currentUser();
        if (me == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        BbsArticle article = requireArticle(form.getArticleId());
        BbsMaster  master  = requireMaster(article.getBbsMasterId());
        if (!"Y".equalsIgnoreCase(master.getCommentYn())) {
            throw new IllegalStateException("댓글이 허용되지 않은 게시판입니다.");
        }

        // 부모 댓글 검증 — 같은 article 소속 + 현재 활성
        int depth = 1;
        BbsComment parent = null;
        if (form.getParentCommentId() != null && !form.getParentCommentId().isBlank()) {
            parent = commentMapper.findById(form.getParentCommentId());
            if (parent == null
                || !parent.getArticleId().equals(article.getArticleId())) {
                throw new ResourceNotFoundException("부모 댓글을 찾을 수 없습니다.");
            }
            // 2 단계까지만 허용 — 부모가 답글이면 본 댓글도 같은 부모를 가지도록 평탄화
            if (parent.getDepth() >= 2 && parent.getParentCommentId() != null) {
                form.setParentCommentId(parent.getParentCommentId());
                depth = 2;
            } else {
                depth = parent.getDepth() + 1;
            }
        }

        BbsComment c = new BbsComment();
        c.setCommentId(UuidV7Generator.generate("CMT"));
        c.setArticleId(article.getArticleId());
        c.setParentCommentId(blankToNull(form.getParentCommentId()));
        c.setWriterUserId(me.getUserId());
        c.setWriterUserType(me.getUserType());
        c.setWriterName(me.getUsername());
        c.setContent(form.getContent());
        c.setSecretYn(normalizeYn(form.getSecretYn()));
        c.setDepth(depth);
        c.setClientIp(clientIp);
        c.setStatus(BbsCommentStatus.PUBLISHED.name());
        c.setDeleteYn("N");

        commentMapper.insert(c);
        articleMapper.recomputeCommentCount(article.getArticleId());

        auditLogger.write(AuditEvent.of("BBS_COMMENT_CREATE", "tb_bbs_comment")
            .withTarget(c.getCommentId())
            .withAfter("{\"articleId\":" + JsonUtils.quote(article.getArticleId())
                + ",\"writerUserId\":" + JsonUtils.quote(c.getWriterUserId())
                + ",\"depth\":" + depth + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_COMMENT_CREATE_OK id={} article={} writer={}",
            c.getCommentId(), article.getArticleId(), c.getWriterUserId());

        // 알림 — 글 작성자에게 새 댓글 알림. 자기 댓글이거나 작성자 미상이면 skip.
        try {
            String articleOwner = article.getWriterUserId();
            if (articleOwner != null && !articleOwner.isBlank()
                    && !articleOwner.equals(c.getWriterUserId())) {
                String linkUrl = "/bbs/" + master.getSiteCode() + "/" + master.getBbsCode()
                    + "/" + article.getArticleId();
                String snippet = c.getContent() == null
                    ? ""
                    : (c.getContent().length() > 80
                        ? c.getContent().substring(0, 80) + "…"
                        : c.getContent());
                notificationService.send(
                    articleOwner,
                    com.gonet.primary.notification.dto.NotificationType.BOARD_COMMENT,
                    "새 댓글: " + (article.getTitle() == null ? "" : article.getTitle()),
                    snippet,
                    linkUrl,
                    "BBS_ARTICLE",
                    article.getArticleId());
            }
        } catch (Exception ex) {
            log.warn("BBS_COMMENT_NOTIFY_FAIL articleId={} reason={}",
                article.getArticleId(), ex.getMessage());
        }

        return c.getCommentId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(BbsCommentSaveForm form, String clientIp) {
        Objects.requireNonNull(form, "form");
        if (form.getCommentId() == null || form.getCommentId().isBlank()) {
            throw new IllegalArgumentException("commentId required");
        }
        BbsComment existing = commentMapper.findById(form.getCommentId());
        if (existing == null) {
            throw new ResourceNotFoundException("댓글을 찾을 수 없습니다.");
        }
        ensureOwnerOnly(existing, currentUser());

        existing.setContent(form.getContent());
        existing.setSecretYn(normalizeYn(form.getSecretYn()));
        existing.setClientIp(clientIp);

        commentMapper.update(existing);

        auditLogger.write(AuditEvent.of("BBS_COMMENT_UPDATE", "tb_bbs_comment")
            .withTarget(existing.getCommentId())
            .withAfter("{\"articleId\":" + JsonUtils.quote(existing.getArticleId()) + "}")
            .withResult("SUCCESS"));
        BbsArticle article = articleMapper.findById(existing.getArticleId());
        BbsMaster  master  = (article == null) ? null : masterMapper.findById(article.getBbsMasterId());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            throw new IllegalArgumentException("commentId required");
        }
        BbsComment existing = commentMapper.findById(commentId);
        if (existing == null) {
            throw new ResourceNotFoundException("이미 삭제되었거나 존재하지 않는 댓글입니다.");
        }
        ensureOwnerOrAdmin(existing, currentUser());

        commentMapper.softDelete(commentId);
        articleMapper.recomputeCommentCount(existing.getArticleId());
        log.info("===BBS_COMMENT_DELETE_OK id={} article={} writer={}",
            commentId, existing.getArticleId(), existing.getWriterUserId());

        auditLogger.write(AuditEvent.of("BBS_COMMENT_DELETE", "tb_bbs_comment")
            .withTarget(commentId)
            .withBefore("{\"articleId\":" + JsonUtils.quote(existing.getArticleId())
                + ",\"writerUserId\":" + JsonUtils.quote(existing.getWriterUserId()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void adminUpdateStatus(String commentId, String statusRaw) {
        CustomUserDetails me = currentUser();
        log.info("===BBS_COMMENT_STATUS_ATTEMPT id={} status={} userId={} userType={}",
            commentId, statusRaw,
            me != null ? me.getUserId() : "null",
            me != null ? me.getUserType() : "null");
        if (!isStaffOrAbove(me)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }
        BbsCommentStatus target = BbsCommentStatus.safeParse(statusRaw);
        if (target == BbsCommentStatus.DELETED) {
            throw new IllegalArgumentException("DELETED 는 별도 삭제 절차로 처리하세요.");
        }
        BbsComment existing = commentMapper.findById(commentId);
        if (existing == null) {
            throw new ResourceNotFoundException("댓글을 찾을 수 없습니다.");
        }
        if (target.name().equals(existing.getStatus())) return;

        commentMapper.updateStatus(commentId, target.name());
        articleMapper.recomputeCommentCount(existing.getArticleId());

        // ── C-2 회귀 방지: REPORTED → PUBLISHED 복귀 시 PENDING 신고 일괄 정리 ──
        if (BbsCommentStatus.PUBLISHED == target
                && BbsCommentStatus.REPORTED.name().equals(existing.getStatus())) {
            String adminId = currentUser() == null ? "SYSTEM" : currentUser().getUserId();
            reportService.resolveOpenReportsForTarget(
                BbsLikeTarget.COMMENT, commentId, adminId,
                "관리자 REPORTED → PUBLISHED 복귀 — 누적 신고 일괄 정리");
        }

        auditLogger.write(AuditEvent.of("BBS_COMMENT_STATUS", "tb_bbs_comment")
            .withTarget(commentId)
            .withBefore("{\"status\":" + JsonUtils.quote(existing.getStatus()) + "}")
            .withAfter("{\"status\":" + JsonUtils.quote(target.name()) + "}")
            .withResult("SUCCESS"));
        existing.setStatus(target.name());
        BbsArticle article = articleMapper.findById(existing.getArticleId());
        BbsMaster  master  = (article == null) ? null : masterMapper.findById(article.getBbsMasterId());
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    private BbsArticle requireArticle(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId required");
        }
        BbsArticle a = articleMapper.findById(articleId);
        if (a == null) {
            throw new ResourceNotFoundException("게시글을 찾을 수 없습니다.");
        }
        return a;
    }

    private BbsMaster requireMaster(String bbsMasterId) {
        BbsMaster m = masterMapper.findById(bbsMasterId);
        if (m == null) {
            throw new ResourceNotFoundException("게시판을 찾을 수 없습니다.");
        }
        return m;
    }

    /**
     * 작성자 본인만 — 댓글 수정용. 관리자도 통과 못함.
     * 본인 판정: {@code writerUserId} 또는 {@code createdBy} 둘 중 하나라도 일치.
     */
    private void ensureOwnerOnly(BbsComment c, CustomUserDetails me) {
        if (me == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        boolean owner = isOwner(c, me);
        log.info("===[BoardCommentService.ensureOwnerOnly] commentId={} userId={} userType={} isOwner={}",
            c.getCommentId(), me.getUserId(), me.getUserType(), owner);
        if (owner) return;
        throw new AccessDeniedException("작성자 본인만 가능합니다.");
    }

    /**
     * 작성자 본인 OR 관리자(userType=ADMIN) — 댓글 삭제용.
     * EMPLOYEE 는 ADMIN 이 아니므로 통과 못함.
     */
    private void ensureOwnerOrAdmin(BbsComment c, CustomUserDetails me) {
        if (me == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        boolean admin = isAdmin(me);
        boolean owner = isOwner(c, me);
        log.info("===[BoardCommentService.ensureOwnerOrAdmin] commentId={} userId={} userType={} isAdmin={} isOwner={}",
            c.getCommentId(), me.getUserId(), me.getUserType(), admin, owner);
        if (admin) return;
        if (owner) return;
        throw new AccessDeniedException("작성자 본인 또는 관리자만 가능합니다.");
    }

    private static boolean isOwner(BbsComment c, CustomUserDetails me) {
        if (c == null || me == null || me.getUserId() == null) return false;
        String mine = me.getUserId();
        return mine.equals(c.getWriterUserId()) || mine.equals(c.getCreatedBy());
    }

    private static boolean isAdmin(CustomUserDetails me) {
        return me != null && "STAFF".equalsIgnoreCase(me.getUserType());
    }

    private static CustomUserDetails currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        Object p = a.getPrincipal();
        return (p instanceof CustomUserDetails ud) ? ud : null;
    }

    /**
     * STAFF 이상 — 댓글 모더레이션·비밀댓글 본문 노출 게이트.
     *
     * <p>역할 계층 root → leaf:
     * {@code ROLE_SUPER → ROLE_ADMIN → ROLE_MANAGER → ROLE_STAFF → ROLE_EMPLOYEE}.
     * closure descendant expansion 으로 STAFF 이상 사용자는 모두 {@code ROLE_STAFF}
     * authority 를 보유. EMPLOYEE 는 STAFF 의 child 라 더 낮음 — 제외.
     */
    private static boolean isStaffOrAbove(CustomUserDetails me) {
        if (me == null) return false;
        return me.getAuthorities().stream()
            .anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority()));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 체크박스 → 'Y'/'N' 정규화. null/공백/그 외 → 'N'. */
    private static String normalizeYn(String raw) {
        return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
    }

    @Override
    public List<BbsComment> findRecentByCreatedBy(String userId, int limit) {
        if (userId == null || userId.isBlank()) return List.of();
        return commentMapper.findRecentByCreatedBy(userId, limit);
    }
}
