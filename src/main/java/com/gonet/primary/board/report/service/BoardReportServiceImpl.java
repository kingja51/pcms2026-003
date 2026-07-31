package com.gonet.primary.board.report.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.mapper.BbsArticleMapper;
import com.gonet.primary.board.comment.dto.BbsComment;
import com.gonet.primary.board.comment.mapper.BbsCommentMapper;
import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.mapper.BbsMasterMapper;
import com.gonet.primary.board.report.dto.BbsReport;
import com.gonet.primary.board.report.dto.BbsReportSaveForm;
import com.gonet.primary.board.report.dto.BbsReportSearch;
import com.gonet.primary.board.report.mapper.BbsReportMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 게시판 신고 서비스 구현 — 자동 REPORTED 전환 포함.
 *
 * <p>설정:
 * <ul>
 *   <li>{@code gopcms.board.report-threshold} — PUBLISHED→REPORTED 자동 전환 임계치 (기본 5)</li>
 * </ul>
 *
 * <p>임계 도달 후에도 관리자가 review 로 PUBLISHED 복귀시키면 카운트 리셋 없이
 * 다시 임계 누적 시까지는 자동 전환 안 함 (PUBLISHED 일 때만 자동 전환).
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class BoardReportServiceImpl extends EgovAbstractServiceImpl implements BoardReportService {

    private static final Logger log = LoggerFactory.getLogger(BoardReportServiceImpl.class);

    private final BbsReportMapper     reportMapper;
    private final BbsArticleMapper    articleMapper;
    private final BbsCommentMapper    commentMapper;
    private final BbsMasterMapper     masterMapper;
    private final AuditLogger         auditLogger;
    private final com.gonet.primary.notification.service.NotificationService notificationService;
    private final int                 autoReportThreshold;

    public BoardReportServiceImpl(BbsReportMapper reportMapper,
                                   BbsArticleMapper articleMapper,
                                   BbsCommentMapper commentMapper,
                                   BbsMasterMapper masterMapper,
                                   AuditLogger auditLogger,
                                   com.gonet.primary.notification.service.NotificationService notificationService,
                                   @Value("${gopcms.board.report-threshold:5}") int autoReportThreshold) {
        this.reportMapper        = reportMapper;
        this.articleMapper       = articleMapper;
        this.commentMapper       = commentMapper;
        this.masterMapper        = masterMapper;
        this.auditLogger         = auditLogger;
        this.notificationService = notificationService;
        this.autoReportThreshold = autoReportThreshold;
    }

    // -------------------------------------------------------------------
    // 사용자 — 신고 적재
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void report(BbsLikeTarget target, String targetId, BbsReportSaveForm form, CustomUserDetails me) {
        if (target == null) {
            throw new IllegalArgumentException("신고 대상 종류가 올바르지 않습니다.");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("신고 대상 ID 가 비어 있습니다.");
        }
        if (me == null || me.getUserId() == null) {
            throw new AccessDeniedException("신고는 로그인 후 이용 가능합니다.");
        }
        if (form == null || form.getReasonCode() == null || form.getReasonCode().isBlank()) {
            throw new IllegalArgumentException("신고 사유를 선택해 주세요.");
        }

        String type   = target.name();
        String userId = me.getUserId();

        BbsReport existing = reportMapper.findByTargetAndReporter(type, targetId, userId);
        if (existing != null) {
            throw new IllegalStateException("이미 신고하신 콘텐츠입니다.");
        }

        BbsReport r = new BbsReport();
        r.setReportId(UuidV7Generator.generate("BRP"));
        r.setTargetType(type);
        r.setTargetId(targetId);
        r.setReporterUserId(userId);
        r.setReporterUserType(safeUpper(me.getUserType(), "MEMBER"));
        r.setSourceUrl(trimUrl(form.getSourceUrl()));
        r.setReasonCode(form.getReasonCode());
        r.setReasonText(blankToNull(form.getReasonText()));
        r.setStatus("PENDING");
        r.setDeleteYn("N");
        reportMapper.insert(r);

        // H-1 race 가드 — 비정규화 컬럼 원자적 증감 (+1).
        // 표시/임계 비교용 count 는 별도 SELECT — race 가 있더라도 임계 도달 판정은
        // 신고가 누적되는 monotonic 한 흐름이라 최소한 N 회의 신고가 누적된 후 정확한
        // threshold 비교가 일어남 (false positive 위주, false negative 없음).
        incrementCount(target, targetId, +1);
        long count = reportMapper.countActiveByTarget(type, targetId);

        // 자동 REPORTED 전환 — ARTICLE / COMMENT 만. CONTENT 는 status 워크플로가 별도라 건너뜀.
        boolean autoReported = false;
        if (count >= autoReportThreshold && target != BbsLikeTarget.CONTENT) {
            int updated = (target == BbsLikeTarget.ARTICLE)
                ? articleMapper.markStatusReportedIfPublished(targetId)
                : commentMapper.markStatusReportedIfPublished(targetId);
            autoReported = (updated > 0);
            if (autoReported) {
                // ── C-2 회귀 방지: 자동 REPORTED 전환 직후 PENDING 신고 일괄 REVIEWED 로 정리 ──
                // 미정리 시 관리자가 PUBLISHED 복귀시켜도 count 가 임계 이상 잔존 → 신고 1건만
                // 추가되면 즉시 재전환되는 무한 루프 발생. 카운트 비정규화도 0 으로 동기화.
                reportMapper.resolvePendingByTarget(type, targetId, "SYSTEM",
                    "임계 도달로 자동 REPORTED 전환 — 모더레이션 큐 진입");
                // PENDING 행 일괄 정리 후 비정규화도 0 으로 명시 reset (절대값 설정).
                syncReportCount(target, targetId, 0);

                log.info("===BBS_AUTO_REPORTED target={}/{} count={} threshold={}",
                    type, targetId, count, autoReportThreshold);
                auditLogger.write(AuditEvent.of("BBS_AUTO_REPORTED",
                        target == BbsLikeTarget.ARTICLE ? "tb_bbs_article" : "tb_bbs_comment")
                    .withTarget(targetId)
                    .withAfter("{\"reportCount\":" + count
                        + ",\"threshold\":" + autoReportThreshold + "}")
                    .withResult("SUCCESS"));

                // 검색 색인 훅 없음 — 검색은 외부 엔진(contextPath=/search)이 담당한다(D10)

                // 알림 — STAFF 이상 broadcast (자동 REPORTED 모더레이션 큐 진입)
                try {
                    String linkUrl = (target == BbsLikeTarget.ARTICLE)
                        ? "/admin/system/board/report"
                        : "/admin/system/board/report";
                    notificationService.sendToStaff(
                        com.gonet.primary.notification.dto.NotificationType.BOARD_REPORT,
                        "신고 누적으로 자동 REPORTED 전환",
                        type + " " + targetId + " 가 " + count + "회 신고되어 모더레이션 큐로 진입했습니다.",
                        linkUrl,
                        type,
                        targetId,
                        userId);
                } catch (Exception ex) {
                    // 알림 실패는 신고 흐름을 막지 않는다 — log only
                    log.warn("BBS_AUTO_REPORTED_NOTIFY_FAIL target={}/{} reason={}",
                        type, targetId, ex.getMessage());
                }
            }
        }

        auditLogger.write(AuditEvent.of("BBS_REPORT_CREATE", "tb_bbs_report")
            .withTarget(r.getReportId())
            .withAfter("{\"targetType\":" + JsonUtils.quote(type)
                + ",\"targetId\":" + JsonUtils.quote(targetId)
                + ",\"reporterUserId\":" + JsonUtils.quote(userId)
                + ",\"reasonCode\":" + JsonUtils.quote(form.getReasonCode())
                + ",\"reportCount\":" + count
                + ",\"autoReported\":" + autoReported + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_REPORT_CREATE_OK target={}/{} reporter={} reason={} count={}",
            type, targetId, userId, form.getReasonCode(), count);
    }

    // -------------------------------------------------------------------
    // 관리자 — 모더레이션
    // -------------------------------------------------------------------

    @Override
    public PageResponse<BbsReport> search(BbsReportSearch search) {
        List<BbsReport> rows = reportMapper.findList(search);
        int total = reportMapper.countList(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public BbsReport getById(String reportId) {
        return reportMapper.findById(reportId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void review(String reportId, String status, String reviewNote, CustomUserDetails admin) {
        if (reportId == null || reportId.isBlank()) {
            throw new IllegalArgumentException("신고 ID 가 비어 있습니다.");
        }
        if (!"REVIEWED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("처리 상태는 REVIEWED 또는 REJECTED 만 허용됩니다.");
        }
        if (admin == null || admin.getUserId() == null) {
            throw new AccessDeniedException("관리자 인증이 필요합니다.");
        }
        BbsReport existing = reportMapper.findById(reportId);
        if (existing == null) {
            throw new ResourceNotFoundException("신고를 찾을 수 없습니다: " + reportId);
        }

        reportMapper.updateReview(reportId, status, admin.getUserId(), blankToNull(reviewNote));

        auditLogger.write(AuditEvent.of("BBS_REPORT_REVIEW", "tb_bbs_report")
            .withTarget(reportId)
            .withBefore("{\"status\":" + JsonUtils.quote(existing.getStatus()) + "}")
            .withAfter("{\"status\":" + JsonUtils.quote(status)
                + ",\"reviewedBy\":" + JsonUtils.quote(admin.getUserId()) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_REPORT_REVIEW_OK id={} status={} reviewer={}",
            reportId, status, admin.getUserId());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void resolveOpenReportsForTarget(BbsLikeTarget target, String targetId,
                                            String reviewedBy, String reviewNote) {
        if (target == null || targetId == null || targetId.isBlank()) return;
        if (target == BbsLikeTarget.CONTENT) return; // CONTENT 신고 워크플로 미적용
        String type = target.name();
        String by   = (reviewedBy == null || reviewedBy.isBlank()) ? "SYSTEM" : reviewedBy;

        int resolved = reportMapper.resolvePendingByTarget(type, targetId, by, blankToNull(reviewNote));
        syncReportCount(target, targetId, 0);

        if (resolved > 0) {
            auditLogger.write(AuditEvent.of("BBS_REPORT_RESOLVE_OPEN", "tb_bbs_report")
                .withTarget(targetId)
                .withAfter("{\"targetType\":" + JsonUtils.quote(type)
                    + ",\"resolvedCount\":" + resolved
                    + ",\"reviewedBy\":" + JsonUtils.quote(by) + "}")
                .withResult("SUCCESS"));
            log.info("===BBS_REPORT_RESOLVE_OPEN target={}/{} resolved={} reviewer={}",
                type, targetId, resolved, by);
        }
    }

    // -------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------

    /** 절대값 설정 — reset(0) / 전체 재계산 시점에만 사용. */
    private void syncReportCount(BbsLikeTarget target, String targetId, long count) {
        if (target == BbsLikeTarget.ARTICLE) {
            articleMapper.updateReportCount(targetId, count);
        } else if (target == BbsLikeTarget.COMMENT) {
            commentMapper.updateReportCount(targetId, count);
        }
        // CONTENT — tb_content 에 report_count 비정규화 컬럼 없음. countActiveByTarget() 으로 즉시 조회.
    }

    /** H-1 race 가드 — 원자적 증감 (신고 +1 만 사용). */
    private void incrementCount(BbsLikeTarget target, String targetId, int delta) {
        if (target == BbsLikeTarget.ARTICLE) {
            articleMapper.incrementReportCount(targetId, delta);
        } else if (target == BbsLikeTarget.COMMENT) {
            commentMapper.incrementReportCount(targetId, delta);
        }
    }


    private static String safeUpper(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s.trim().toUpperCase();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** source_url 컬럼 폭(1000) 보호 + 빈 값 정규화. */
    private static String trimUrl(String url) {
        if (url == null) return null;
        String t = url.trim();
        if (t.isEmpty()) return null;
        return t.length() > 1000 ? t.substring(0, 1000) : t;
    }

    @Override
    public List<BbsReport> findRecentByReporter(String userId, int limit) {
        if (userId == null || userId.isBlank()) return List.of();
        return reportMapper.findRecentByReporter(userId, limit);
    }
}
