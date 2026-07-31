package com.gonet.primary.board.article.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.common.util.XssSanitizer;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.dto.BbsArticleSaveForm;
import com.gonet.primary.board.article.dto.BbsArticleSearch;
import com.gonet.primary.board.article.dto.BbsArticleStatus;
import com.gonet.primary.board.article.mapper.BbsArticleMapper;
import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.report.service.BoardReportService;
import com.gonet.primary.board.master.dto.ReadAuth;
import com.gonet.primary.board.master.dto.WriteAuth;
import com.gonet.primary.board.master.mapper.BbsMasterMapper;
import com.gonet.primary.file.dto.FileGroup;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.mapper.FileGroupMapper;
import com.gonet.primary.file.mapper.FileMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 게시글 서비스 구현 — eGov {@link EgovAbstractServiceImpl} 상속.
 *
 * <p>권한 정책:
 * <ul>
 *   <li>write_auth = ALL/GUEST  : 누구나 (B2.1 에서는 GUEST 작성 미지원 — MEMBER 이상으로 격상)</li>
 *   <li>write_auth = MEMBER     : MEMBER/EMPLOYEE/ADMIN</li>
 *   <li>write_auth = EMPLOYEE   : EMPLOYEE/ADMIN</li>
 *   <li>write_auth = ADMIN      : ADMIN 만</li>
 * </ul>
 *
 * <p>secret 글 본문 조회: 작성자 본인 또는 ROLE_STAFF 이상.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class BoardArticleServiceImpl extends EgovAbstractServiceImpl implements BoardArticleService {

    private static final Logger log = LoggerFactory.getLogger(BoardArticleServiceImpl.class);

    private final BbsArticleMapper articleMapper;
    private final BbsMasterMapper  masterMapper;
    private final FileGroupMapper  fileGroupMapper;
    private final FileMapper       fileMapper;
    private final AuditLogger      auditLogger;
    private final XssSanitizer     xssSanitizer;
    /** REPORTED → PUBLISHED 복귀 시 PENDING 신고 일괄 정리 (C-2 회귀 방지). */
    private final BoardReportService reportService;

    public BoardArticleServiceImpl(BbsArticleMapper articleMapper,
                                    BbsMasterMapper masterMapper,
                                    FileGroupMapper fileGroupMapper,
                                    FileMapper fileMapper,
                                    AuditLogger auditLogger,
                                    XssSanitizer xssSanitizer,
                                    BoardReportService reportService) {
        this.articleMapper      = articleMapper;
        this.masterMapper       = masterMapper;
        this.fileGroupMapper    = fileGroupMapper;
        this.fileMapper         = fileMapper;
        this.auditLogger        = auditLogger;
        this.xssSanitizer       = xssSanitizer;
        this.reportService      = reportService;
    }

    /**
     * 저장 직전 content 정제.
     * <ul>
     *   <li>master.html_yn = 'Y' → XSS 화이트리스트 정책 적용 후 저장 (서식 유지)</li>
     *   <li>master.html_yn = 'N' → 모든 태그 제거 후 평문만 저장 (DB 에 raw HTML 잔존 차단)</li>
     * </ul>
     */
    private String sanitizeContent(String raw, BbsMaster master) {
        if (raw == null) return null;
        if ("Y".equalsIgnoreCase(master.getHtmlYn())) {
            return xssSanitizer.safe(raw);
        }
        return xssSanitizer.stripAll(raw);
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override
    public List<BbsArticle> search(BbsArticleSearch search) {
        if (search == null) throw new IllegalArgumentException("search required");
        if (search.getBbsMasterId() == null || search.getBbsMasterId().isBlank()) {
            throw new IllegalArgumentException("bbsMasterId required");
        }
        return articleMapper.findList(search);
    }

    @Override
    public int count(BbsArticleSearch search) {
        if (search == null) throw new IllegalArgumentException("search required");
        if (search.getBbsMasterId() == null || search.getBbsMasterId().isBlank()) return 0;
        return articleMapper.countList(search);
    }

    @Override
    public BbsArticle get(String bbsMasterId, String articleId) {
        if (bbsMasterId == null || articleId == null) return null;
        return articleMapper.findByBbsAndId(bbsMasterId, articleId);
    }

    @Override
    public BbsArticle getById(String articleId) {
        if (articleId == null || articleId.isBlank()) return null;
        return articleMapper.findById(articleId);
    }

    @Override
    public BbsArticle getPrev(String bbsMasterId, BbsArticle current) {
        if (bbsMasterId == null || current == null || current.getCreatedAt() == null) return null;
        return articleMapper.findPrev(bbsMasterId, current.getCreatedAt(), current.getArticleId());
    }

    @Override
    public BbsArticle getNext(String bbsMasterId, BbsArticle current) {
        if (bbsMasterId == null || current == null || current.getCreatedAt() == null) return null;
        return articleMapper.findNext(bbsMasterId, current.getCreatedAt(), current.getArticleId());
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(BbsArticleSaveForm form, String clientIp) {
        Objects.requireNonNull(form, "form");
        BbsMaster master = requireMaster(form.getBbsMasterId());
        ensureUseable(master);

        CustomUserDetails me = currentUser();
        log.info("===BBS_ARTICLE_CREATE_ATTEMPT bbs={} userId={} userType={}",
            master.getBbsCode(),
            me != null ? me.getUserId() : "null",
            me != null ? me.getUserType() : "null");
        ensureCanWrite(master, me);

        BbsArticle a = new BbsArticle();
        // articleId 는 작성 폼에서 사전 발급된 값 사용 — 폼 GET 시점에 picker 가 entityId 로 사용
        // 했기 때문. 비어있으면 새로 생성.
        String articleId = blankToNull(form.getArticleId()) != null
            ? form.getArticleId().trim()
            : UuidV7Generator.generate("ART");
        a.setArticleId(articleId);
        a.setBbsMasterId(master.getBbsMasterId());
        a.setCategoryId(blankToNull(form.getCategoryId()));

        if (me != null) {
            a.setWriterUserId(me.getUserId());
            a.setWriterUserType(me.getUserType());
            // 작성자명 정책:
            //   - STAFF 이상(EMPLOYEE/ADMIN/ROLE_STAFF): form 입력값 신뢰 — 비어있으면 본인 이름 fallback
            //   - 일반 MEMBER: form 값 무시, 항상 본인 이름으로 강제 (위·변조 방지)
            String fallbackName = me.getUsername();
            if (isStaffOrAbove(me) && blankToNull(form.getWriterName()) != null) {
                a.setWriterName(form.getWriterName().trim());
            } else {
                a.setWriterName(fallbackName);
            }
        } else {
            // B2.1 에서는 GUEST 작성을 막아두었으므로 여기까지 오면 안 됨
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        a.setTitle(form.getTitle().trim());
        a.setContent(sanitizeContent(form.getContent(), master));
        a.setPressName(blankToNull(form.getPressName()));
        a.setLinkUrl(blankToNull(form.getLinkUrl()));
        a.setPublishedAt(parseDate(form.getPublishedAt()));
        a.setSecretYn(BbsArticleSaveForm.yn(form.getSecretYn()));
        // notice_yn 은 관리자만 토글 가능. 일반 사용자가 보내도 N
        a.setNoticeYn(isStaffOrAbove(me)
            ? BbsArticleSaveForm.yn(form.getNoticeYn())
            : "N");
        a.setViewCount(0L);
        a.setLikeCount(0L);
        a.setCommentCount(0);
        a.setClientIp(clientIp);
        a.setStatus(BbsArticleStatus.PUBLISHED.name());
        a.setDeleteYn("N");

        // ── 첨부 파일그룹 처리 ──
        // 사용자가 picker 로 파일을 업로드한 경우 file_group 이 이미 (entityType='BBS',
        // entityId=articleId) 로 만들어져 있다. 없으면 빈 그룹을 새로 만든다.
        // tb_bbs_article.file_group_id 는 NOT NULL 이므로 INSERT 전에 반드시 보장되어야 함.
        // 공지글(notice_yn='Y') 은 마스터 정책 무관 ANONYMOUS 강제.
        String articleDownloadAuth = resolveArticleDownloadAuth(
            a.getNoticeYn(), master.getDownloadAuth());
        FileGroup group = ensureFileGroup(articleId, master.getSiteId(), articleDownloadAuth);
        a.setFileGroupId(group.getFileGroupId());

        // 사용자가 picker UI 에서 X 버튼으로 뺀 파일은 attachmentsJson 배열에서 빠져있다.
        // 그룹의 다른 파일을 soft delete 하여 동기화.
        syncAttachments(group.getFileGroupId(), parseAttachments(form.getAttachmentsJson()));

        articleMapper.insert(a);

        auditLogger.write(AuditEvent.of("BBS_ARTICLE_CREATE", "tb_bbs_article")
            .withTarget(a.getArticleId())
            .withAfter("{\"bbsMasterId\":" + JsonUtils.quote(master.getBbsMasterId())
                + ",\"title\":" + JsonUtils.quote(a.getTitle())
                + ",\"writerUserId\":" + JsonUtils.quote(a.getWriterUserId())
                + ",\"secretYn\":" + JsonUtils.quote(a.getSecretYn()) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_ARTICLE_CREATE_OK id={} bbs={} writer={}",
            a.getArticleId(), master.getBbsCode(), a.getWriterUserId());

        return a.getArticleId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(BbsArticleSaveForm form, String clientIp) {
        Objects.requireNonNull(form, "form");
        if (form.getArticleId() == null || form.getArticleId().isBlank()) {
            throw new IllegalArgumentException("articleId required");
        }
        BbsArticle existing = articleMapper.findById(form.getArticleId());
        if (existing == null) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }
        // master 가 동시 삭제되는 race 시에도 update 시점에 일관된 정책 사용 (사후 조회 시 m==null 분기 회피).
        BbsMaster master = masterMapper.findById(existing.getBbsMasterId());
        CustomUserDetails me = currentUser();
        log.info("===BBS_ARTICLE_UPDATE_ATTEMPT id={} userId={} userType={}",
            form.getArticleId(),
            me != null ? me.getUserId() : "null",
            me != null ? me.getUserType() : "null");
        ensureOwnerOnly(existing, me);

        existing.setTitle(form.getTitle().trim());
        existing.setContent(sanitizeContent(form.getContent(), master));
        existing.setPressName(blankToNull(form.getPressName()));
        existing.setLinkUrl(blankToNull(form.getLinkUrl()));
        existing.setPublishedAt(parseDate(form.getPublishedAt()));
        existing.setSecretYn(BbsArticleSaveForm.yn(form.getSecretYn()));
        // notice_yn 은 관리자만 변경
        if (isStaffOrAbove(me)) {
            existing.setNoticeYn(BbsArticleSaveForm.yn(form.getNoticeYn()));
            // STAFF 이상은 작성자명도 수정 가능 — 비어있으면 그대로 둠
            String w = blankToNull(form.getWriterName());
            if (w != null) {
                existing.setWriterName(w);
            }
        }
        // 일반 MEMBER 의 form.writerName 은 무시 — existing.writerName 보존 (위·변조 방지)
        existing.setCategoryId(blankToNull(form.getCategoryId()));
        existing.setClientIp(clientIp);

        articleMapper.update(existing);

        // 첨부 파일 동기화 — picker 로 빼낸 파일을 soft delete
        if (existing.getFileGroupId() != null) {
            syncAttachments(existing.getFileGroupId(), parseAttachments(form.getAttachmentsJson()));
            // 공지글(notice_yn='Y') 은 ANONYMOUS, 그 외는 master.download_auth 사용.
            if (master != null) {
                String desired = resolveArticleDownloadAuth(
                    existing.getNoticeYn(), master.getDownloadAuth());
                FileGroup g = fileGroupMapper.findById(existing.getFileGroupId());
                if (g != null && !desired.equals(g.getDownloadAuth())) {
                    fileGroupMapper.updateDownloadAuth(g.getFileGroupId(), desired, null, null);
                }
            }
        }

        auditLogger.write(AuditEvent.of("BBS_ARTICLE_UPDATE", "tb_bbs_article")
            .withTarget(existing.getArticleId())
            .withAfter("{\"title\":" + JsonUtils.quote(existing.getTitle())
                + ",\"secretYn\":" + JsonUtils.quote(existing.getSecretYn())
                + ",\"noticeYn\":" + JsonUtils.quote(existing.getNoticeYn()) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_ARTICLE_UPDATE_OK id={}", existing.getArticleId());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId required");
        }
        BbsArticle existing = articleMapper.findById(articleId);
        if (existing == null) {
            throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 게시글입니다.");
        }
        CustomUserDetails me = currentUser();
        log.info("===BBS_ARTICLE_DELETE_ATTEMPT id={} userId={} userType={}",
            articleId,
            me != null ? me.getUserId() : "null",
            me != null ? me.getUserType() : "null");
        ensureOwnerOrAdmin(existing, me);

        int affected = articleMapper.softDelete(articleId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + articleId);
        }
        log.info("===BBS_ARTICLE_DELETE_OK id={} writer={}", articleId, existing.getWriterUserId());

        auditLogger.write(AuditEvent.of("BBS_ARTICLE_DELETE", "tb_bbs_article")
            .withTarget(articleId)
            .withBefore("{\"title\":" + JsonUtils.quote(existing.getTitle())
                + ",\"writerUserId\":" + JsonUtils.quote(existing.getWriterUserId()) + "}")
            .withResult("SUCCESS"));

        // 첨부 파일 일괄 비활성 — 같은 트랜잭션. 물리 삭제는 retention(FileRetentionTarget) 이 180일 후 회수.
        cascadeFileGroupSoftDelete(existing.getFileGroupId(), articleId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void adminUpdateStatus(String articleId, String statusRaw) {
        CustomUserDetails me = currentUser();
        log.info("===BBS_ARTICLE_STATUS_ATTEMPT id={} status={} userId={} userType={}",
            articleId, statusRaw,
            me != null ? me.getUserId() : "null",
            me != null ? me.getUserType() : "null");
        if (!isStaffOrAbove(me)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }
        BbsArticleStatus status = BbsArticleStatus.safeParse(statusRaw);
        if (status == BbsArticleStatus.DELETED) {
            throw new IllegalArgumentException("DELETED 는 별도 삭제 절차로 처리하세요.");
        }
        BbsArticle existing = articleMapper.findById(articleId);
        if (existing == null) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }
        if (status.name().equals(existing.getStatus())) {
            return; // idempotent
        }

        articleMapper.updateStatus(articleId, status.name());

        auditLogger.write(AuditEvent.of("BBS_ARTICLE_STATUS", "tb_bbs_article")
            .withTarget(articleId)
            .withBefore("{\"status\":" + JsonUtils.quote(existing.getStatus()) + "}")
            .withAfter("{\"status\":" + JsonUtils.quote(status.name()) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_ARTICLE_STATUS_OK id={} {} -> {}",
            articleId, existing.getStatus(), status);

        // ── C-2 회귀 방지: REPORTED → PUBLISHED 복귀 시 PENDING 신고 일괄 정리 ──
        // 미정리 시 다음 신고 1건이 들어오면 즉시 임계 재도달 → 무한 REPORTED 재전환.
        if (BbsArticleStatus.PUBLISHED == status
                && BbsArticleStatus.REPORTED.name().equals(existing.getStatus())) {
            String adminId = currentUser() == null ? "SYSTEM" : currentUser().getUserId();
            reportService.resolveOpenReportsForTarget(
                BbsLikeTarget.ARTICLE, articleId, adminId,
                "관리자 REPORTED → PUBLISHED 복귀 — 누적 신고 일괄 정리");
        }
        BbsMaster m4Idx = masterMapper.findById(existing.getBbsMasterId());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void adminToggleNotice(String articleId, boolean notice) {
        if (!isStaffOrAbove(currentUser())) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }
        BbsArticle existing = articleMapper.findById(articleId);
        if (existing == null) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }
        String next = notice ? "Y" : "N";
        if (next.equals(existing.getNoticeYn())) {
            return;
        }
        articleMapper.updateNoticeYn(articleId, next);

        // 공지 토글에 따라 file_group.download_auth 동기화:
        //   notice=Y → ANONYMOUS (마스터 정책과 무관, 공지는 누구나 첨부 받기 보장)
        //   notice=N → master.download_auth 로 복귀
        if (existing.getFileGroupId() != null) {
            BbsMaster m = masterMapper.findById(existing.getBbsMasterId());
            String desired = resolveArticleDownloadAuth(
                next, m == null ? null : m.getDownloadAuth());
            FileGroup g = fileGroupMapper.findById(existing.getFileGroupId());
            if (g != null && !desired.equals(g.getDownloadAuth())) {
                fileGroupMapper.updateDownloadAuth(g.getFileGroupId(), desired, null, null);
                log.info("===BBS_ARTICLE_NOTICE_FG_SYNC articleId={} fileGroupId={} {}->{}",
                    articleId, g.getFileGroupId(), g.getDownloadAuth(), desired);
            }
        }

        auditLogger.write(AuditEvent.of("BBS_ARTICLE_NOTICE", "tb_bbs_article")
            .withTarget(articleId)
            .withBefore("{\"noticeYn\":" + JsonUtils.quote(existing.getNoticeYn()) + "}")
            .withAfter("{\"noticeYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    public List<FileItem> listAttachments(String fileGroupId) {
        if (fileGroupId == null || fileGroupId.isBlank()) return List.of();
        return fileMapper.findAllByGroupId(fileGroupId);
    }

    @Override
    public String findCreatedBy(String articleId) {
        if (articleId == null || articleId.isBlank()) return null;
        return articleMapper.findCreatedBy(articleId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void incrementViewCount(String articleId) {
        if (articleId == null || articleId.isBlank()) return;
        articleMapper.incrementViewCount(articleId);
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    private BbsMaster requireMaster(String bbsMasterId) {
        if (bbsMasterId == null || bbsMasterId.isBlank()) {
            throw new IllegalArgumentException("게시판이 지정되지 않았습니다.");
        }
        BbsMaster m = masterMapper.findById(bbsMasterId);
        if (m == null) {
            throw new IllegalArgumentException("게시판을 찾을 수 없습니다: " + bbsMasterId);
        }
        return m;
    }

    private static void ensureUseable(BbsMaster m) {
        if (!"Y".equalsIgnoreCase(m.getUseYn())) {
            throw new IllegalStateException("사용중지된 게시판입니다.");
        }
    }

    @Override
    public void ensureCanRead(BbsMaster master, CustomUserDetails me) {
        if (master == null) {
            throw new IllegalArgumentException("게시판 정보가 없습니다.");
        }
        ReadAuth readAuth = ReadAuth.safeParse(master.getReadAuth());
        if (readAuth == ReadAuth.ALL) return;       // 누구나
        if (me == null) {
            log.info("===[BoardArticleService.ensureCanRead] Denied: me is null, readAuth={}", readAuth);
            throw new AccessDeniedException(
                "이 게시판은 " + readAuth + " 이상만 열람할 수 있습니다. 로그인이 필요합니다.");
        }
        String userType = orDefault(me.getUserType(), "MEMBER").toUpperCase(Locale.ROOT);
        boolean ok = switch (readAuth) {
            case MEMBER   -> true;                                // 로그인이면 OK
            case EMPLOYEE -> "EMPLOYEE".equals(userType) || "STAFF".equals(userType) || "ADMIN".equals(userType);
            case ADMIN    -> "STAFF".equals(userType) || "ADMIN".equals(userType);
            case ALL      -> true;                                // unreachable — 위에서 early return
        };
        log.info("===[BoardArticleService.ensureCanRead] userType={}, readAuth={}, result={}", userType, readAuth, ok);
        if (!ok) {
            throw new AccessDeniedException(
                "이 게시판은 " + readAuth + " 이상만 열람할 수 있습니다.");
        }
    }

    @Override
    public void ensureCanWrite(BbsMaster master, CustomUserDetails me) {
        // 비로그인 작성은 미지원 — write_auth 와 무관하게 인증 필수
        if (me == null) {
            log.info("===[BoardArticleService.ensureCanWrite] Denied: me is null");
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        WriteAuth writeAuth = WriteAuth.safeParse(master.getWriteAuth());
        String   userType   = orDefault(me.getUserType(), "MEMBER").toUpperCase(Locale.ROOT);
        boolean ok = switch (writeAuth) {
            case MEMBER   -> true;                                // 로그인만 되어 있으면 OK
            case EMPLOYEE -> "EMPLOYEE".equals(userType) || "STAFF".equals(userType) || "ADMIN".equals(userType);
            case ADMIN    -> "STAFF".equals(userType) || "ADMIN".equals(userType);
        };
        log.info("===[BoardArticleService.ensureCanWrite] userType={}, writeAuth={}, result={}", userType, writeAuth, ok);
        if (!ok) {
            throw new AccessDeniedException(
                "이 게시판은 " + writeAuth + " 이상만 작성할 수 있습니다.");
        }
    }

    /**
     * 수정 권한 — 작성자 본인 OR STAFF 이상.
     * 본인 판정: {@code me.userId} 가 article 의 {@code writerUserId} 또는 {@code createdBy}
     * 둘 중 하나라도 일치하면 본인. STAFF 는 작성자명·본문을 대신 수정할 수 있다.
     */
    private void ensureOwnerOnly(BbsArticle a, CustomUserDetails me) {
        if (me == null) {
            log.info("===[BoardArticleService.ensureOwnerOnly] Denied: me is null");
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        boolean staff = isStaffOrAbove(me);
        boolean owner = isOwner(a, me);
        log.info("===[BoardArticleService.ensureOwnerOnly] articleId={}, userId={}, isStaff={}, isOwner={}", 
            a.getArticleId(), me.getUserId(), staff, owner);
        if (staff) return;
        if (owner) return;
        throw new AccessDeniedException("작성자 본인 또는 관리자만 가능합니다.");
    }

    /**
     * 작성자 본인 OR 관리자(userType=ADMIN) — 삭제용.
     * EMPLOYEE 는 ADMIN 이 아니므로 통과 못함 (모더레이션 권한과 분리).
     */
    private void ensureOwnerOrAdmin(BbsArticle a, CustomUserDetails me) {
        if (me == null) {
            log.info("===[BoardArticleService.ensureOwnerOrAdmin] Denied: me is null");
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        boolean admin = isAdmin(me);
        boolean owner = isOwner(a, me);
        log.info("===[BoardArticleService.ensureOwnerOrAdmin] articleId={}, userId={}, isAdmin={}, isOwner={}", 
            a.getArticleId(), me.getUserId(), admin, owner);
        if (admin) return;
        if (owner) return;
        throw new AccessDeniedException("작성자 본인 또는 관리자만 가능합니다.");
    }

    /**
     * 본인 판정 — {@code writerUserId} 또는 {@code createdBy} 중 하나라도 일치.
     * createdBy 는 BaseEntity 의 6감사컬럼 — AuditInterceptor 가 INSERT 시 채운 사용자 PK.
     */
    private static boolean isOwner(BbsArticle a, CustomUserDetails me) {
        if (a == null || me == null || me.getUserId() == null) return false;
        String mine = me.getUserId();
        return mine.equals(a.getWriterUserId()) || mine.equals(a.getCreatedBy());
    }

    /** userType == STAFF 만 — EMPLOYEE 제외. */
    private static boolean isAdmin(CustomUserDetails me) {
        if (me == null) return false;
        String type = me.getUserType();
        return "STAFF".equalsIgnoreCase(type) || "ADMIN".equalsIgnoreCase(type);
    }

    /**
     * 현재 인증된 사용자. 비인증/HTTP 밖이면 null.
     */
    private static CustomUserDetails currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        Object p = a.getPrincipal();
        if (p instanceof CustomUserDetails ud) return ud;
        return null;
    }

    /**
     * ROLE_STAFF 이상 여부 — 모더레이션·작성자명 변경 등 권한 게이트.
     *
     * <p>역할 계층 (root → leaf, 위가 더 강함):
     * <pre>
     *   ROLE_SUPER → ROLE_ADMIN → ROLE_MANAGER → ROLE_STAFF → ROLE_EMPLOYEE
     * </pre>
     * <p>{@code tb_role_hierarchy} closure 가 descendant expansion 으로 동작하므로
     * STAFF 이상 사용자는 모두 {@code ROLE_STAFF} authority 를 보유한다 (SUPER/ADMIN/MANAGER 가
     * STAFF 를 자손으로 포함). 따라서 단일 키 체크만으로 STAFF·이상 모두 매칭.
     *
     * <p>{@code ROLE_EMPLOYEE} 는 STAFF 의 child 로 권한이 더 낮아 모더레이션에서 제외.
     * {@code userType == EMPLOYEE} 도 도메인 분류일 뿐 STAFF 권한과 무관 — fallback 제거.
     */
    private static boolean isStaffOrAbove(CustomUserDetails me) {
        if (me == null) return false;
        boolean result = false;
        for (GrantedAuthority ga : me.getAuthorities()) {
            String auth = ga.getAuthority();
            if ("ROLE_STAFF".equals(auth) || "ROLE_ADMIN".equals(auth) || "ADMIN".equals(auth)) {
                result = true;
                break;
            }
        }
        log.info("===[BoardArticleService.isStaffOrAbove] userId={}, authorities={}, result={}", me.getUserId(), me.getAuthorities(), result);
        return result;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * 폼의 yyyy-MM-dd 문자열을 LocalDate 로 변환. 빈 값/잘못된 포맷은 null 반환 (검증 단에서 차단할 일).
     */
    private static java.time.LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private static String orDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    /**
     * 게시글의 download_auth 정책 해석.
     * <p><strong>공지글(notice_yn='Y') 은 항상 ANONYMOUS</strong> — 마스터 정책과 무관.
     * 일반 게시글은 마스터 download_auth 사용.
     *
     * @param noticeYn         {@code 'Y'} 면 ANONYMOUS 강제
     * @param masterDownloadAuth tb_bbs_master.download_auth (null/blank 시 ROLE_MEMBER)
     */
    static String resolveArticleDownloadAuth(String noticeYn, String masterDownloadAuth) {
        if ("Y".equalsIgnoreCase(noticeYn)) {
            return "ANONYMOUS";
        }
        return (masterDownloadAuth == null || masterDownloadAuth.isBlank())
            ? "ROLE_MEMBER" : masterDownloadAuth.trim();
    }

    /**
     * (entityType='BBS', entityId=articleId) 의 file_group 을 보장한다.
     * 사용자가 picker 로 파일을 미리 업로드한 경우 FileServiceImpl 이 만들어 둔 그룹을 재사용,
     * 없으면 빈 그룹을 새로 INSERT 하여 tb_bbs_article.file_group_id NOT NULL FK 를 만족.
     *
     * <p>{@code masterDownloadAuth} 는 tb_bbs_master.download_auth 값 그대로 사용.
     * 기존 그룹이 있는데 정책이 다르면 즉시 동기화.
     */
    private FileGroup ensureFileGroup(String articleId, String siteId, String masterDownloadAuth) {
        String downloadAuth = (masterDownloadAuth == null || masterDownloadAuth.isBlank())
            ? "ROLE_MEMBER"
            : masterDownloadAuth.trim();
        FileGroup g = fileGroupMapper.findByEntity("BBS", articleId);
        if (g != null) {
            if (!downloadAuth.equals(g.getDownloadAuth())) {
                fileGroupMapper.updateDownloadAuth(g.getFileGroupId(), downloadAuth, null, null);
                g.setDownloadAuth(downloadAuth);
            }
            return g;
        }
        g = new FileGroup();
        g.setFileGroupId(UuidV7Generator.generate("FG0"));
        g.setEntityType("BBS");
        g.setEntityId(articleId);
        g.setSiteId(siteId);
        g.setDownloadAuth(downloadAuth);
        g.setDeleteYn("N");
        fileGroupMapper.insert(g);
        return g;
    }

    /** picker hidden input JSON 파싱용 — thread-safe singleton. */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** UUID v7 (36자) 또는 prefix 형태("PFX_<UUID>", 40자) 검증 정규식. */
    private static final java.util.regex.Pattern FILE_ID_PATTERN =
        java.util.regex.Pattern.compile("([A-Z0-9]{3}_)?[0-9a-fA-F-]{36}");

    /**
     * picker hidden input 의 fileId JSON 배열을 파싱.
     * 형식 예: {@code ["019d-...","019d-..."]}.
     *
     * <p>Jackson {@code ObjectMapper.readValue} 로 정식 파싱 — 형식 오류 시 빈 리스트 반환,
     * 비즈니스 흐름 차단하지 않는다. 각 토큰은 UUID/prefix 정규식으로 추가 검증.
     */
    static List<String> parseAttachments(String json) {
        if (json == null || json.isBlank()) return List.of();
        String[] arr;
        try {
            arr = JSON.readValue(json, String[].class);
        } catch (Exception ex) {
            log.warn("BBS_ARTICLE_ATTACHMENTS_PARSE_FAIL reason={} input={}",
                ex.getMessage(), json.length() > 200 ? json.substring(0, 200) + "..." : json);
            return List.of();
        }
        if (arr == null || arr.length == 0) return List.of();
        List<String> out = new ArrayList<>(arr.length);
        for (String s : arr) {
            if (s == null) continue;
            String t = s.trim();
            if (FILE_ID_PATTERN.matcher(t).matches()) out.add(t);
        }
        return out;
    }

    /**
     * 게시글 soft-delete 시 file_group 내 모든 활성 파일을 일괄 soft-delete.
     *
     * <p>article delete_yn='Y' 와 같은 트랜잭션에 묶여 정합성 보장.
     * file_group 자체는 그대로 두고 tb_file 행만 비활성 — 그룹 단위 hard 삭제는
     * SoftDeleteRetentionScheduler(FileRetentionTarget) 가 retention(180일) 경과 후 처리.
     *
     * <p>fileGroupId 가 null/blank 이면 첨부 없는 글이라 no-op.
     */
    private void cascadeFileGroupSoftDelete(String fileGroupId, String articleId) {
        if (fileGroupId == null || fileGroupId.isBlank()) return;
        List<FileItem> currentFiles = fileMapper.findAllByGroupId(fileGroupId);
        for (FileItem f : currentFiles) {
            fileMapper.softDelete(f.getFileId());
            auditLogger.write(AuditEvent.of("BBS_ARTICLE_FILE_REMOVE", "tb_file")
                .withTarget(f.getFileId())
                .withBefore("{\"originalName\":" + JsonUtils.quote(f.getOriginalName())
                    + ",\"fileGroupId\":" + JsonUtils.quote(fileGroupId)
                    + ",\"articleId\":" + JsonUtils.quote(articleId)
                    + ",\"reason\":\"ARTICLE_DELETE\"}")
                .withResult("SUCCESS"));
        }
        log.info("===BBS_ARTICLE_FILE_CASCADE_OK articleId={} fileGroupId={} count={}",
            articleId, fileGroupId, currentFiles.size());
    }

    /**
     * 첨부 동기화 — 그룹 내 모든 활성 파일 중 keep 에 없는 것을 soft delete.
     * 작성/수정 화면에서 사용자가 X 로 빼낸 파일을 서버에서 정리.
     */
    private void syncAttachments(String fileGroupId, List<String> keepFileIds) {
        if (fileGroupId == null) return;
        Set<String> keep = new HashSet<>(keepFileIds);
        List<FileItem> currentFiles = fileMapper.findAllByGroupId(fileGroupId);
        for (FileItem f : currentFiles) {
            if (!keep.contains(f.getFileId())) {
                fileMapper.softDelete(f.getFileId());
                auditLogger.write(AuditEvent.of("BBS_ARTICLE_FILE_REMOVE", "tb_file")
                    .withTarget(f.getFileId())
                    .withBefore("{\"originalName\":" + JsonUtils.quote(f.getOriginalName())
                        + ",\"fileGroupId\":" + JsonUtils.quote(fileGroupId) + "}")
                    .withResult("SUCCESS"));
            }
        }
    }

    @Override
    public List<BbsArticle> findRecentByCreatedBy(String userId, int limit) {
        if (userId == null || userId.isBlank()) return List.of();
        return articleMapper.findRecentByCreatedBy(userId, limit);
    }
}
