package com.gonet.primary.complaint.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.IpUtils;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.complaint.dto.*;
import com.gonet.primary.complaint.mapper.ComplaintArticleMapper;
import com.gonet.primary.file.dto.FileGroup;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.mapper.FileGroupMapper;
import com.gonet.primary.file.mapper.FileMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true,
    transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class ComplaintArticleServiceImpl extends EgovAbstractServiceImpl
        implements ComplaintArticleService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ComplaintArticleMapper mapper;
    private final AuditLogger            auditLogger;
    private final EmailHasher            hasher;
    private final FileGroupMapper        fileGroupMapper;
    private final FileMapper             fileMapper;

    public ComplaintArticleServiceImpl(ComplaintArticleMapper mapper,
                                       AuditLogger auditLogger,
                                       EmailHasher hasher,
                                       FileGroupMapper fileGroupMapper,
                                       FileMapper fileMapper) {
        this.mapper          = mapper;
        this.auditLogger     = auditLogger;
        this.hasher          = hasher;
        this.fileGroupMapper = fileGroupMapper;
        this.fileMapper      = fileMapper;
    }

    @Override
    public PageResponse<ComplaintArticle> list(ComplaintArticleSearch search) {
        List<ComplaintArticle> rows  = mapper.list(search);
        int                    total = mapper.count(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public PageResponse<ComplaintArticle> listForUser(ComplaintArticleSearch search) {
        List<ComplaintArticle> rows  = mapper.listForUser(search);
        int                    total = mapper.countForUser(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public ComplaintArticle get(String articleId) {
        return mapper.findById(articleId);
    }

    @Override
    public ComplaintArticle getByComplaintNo(String complaintMasterId, String complaintNo) {
        return mapper.findByComplaintNo(complaintMasterId, complaintNo);
    }

    @Override
    public boolean isOwnerOrStaff(ComplaintArticle article, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        // STAFF 이상 — 모든 민원 열람 허용
        boolean isStaff = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_STAFF") || a.equals("ROLE_ADMIN")
                        || a.equals("ROLE_EMPLOYEE"));
        if (isStaff) return true;
        // 본인 확인 — 우선순위: di_hash > oauth_uid_hash > member_id
        String principal = auth.getName();
        if (article.getWriterMemberId() != null
                && article.getWriterMemberId().equals(principal)) return true;
        return false;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String submit(ComplaintMaster master, ComplaintArticleSaveForm form,
                         Authentication auth) {
        // GET 단계에서 사전 발급된 articleId 재사용 (file-picker entityId 와 동일)
        String articleId = notBlank(form.getArticleId())
            ? form.getArticleId()
            : UuidV7Generator.generate();

        ComplaintArticle article = new ComplaintArticle();
        article.setArticleId(articleId);
        article.setComplaintMasterId(master.getComplaintMasterId());
        article.setCategoryId(notBlank(form.getCategoryId()) ? form.getCategoryId() : null);

        // 첨부파일 그룹 — picker 가 이미 (COMPLAINT, articleId) 그룹을 만들었으면 재사용
        if ("Y".equalsIgnoreCase(master.getFileYn())) {
            FileGroup group = ensureComplaintFileGroup(articleId, master.getSiteId());
            article.setFileGroupId(group.getFileGroupId());
            syncAttachments(group.getFileGroupId(), parseAttachments(form.getAttachmentsJson()));
        }

        // 작성자 식별 — form 에서 Controller 가 채운 값 그대로 사용
        article.setWriterAuthType(form.getWriterAuthType());
        article.setWriterMemberId(form.getWriterMemberId());
        article.setWriterDiHash(form.getWriterDiHash());
        article.setWriterOauthProvider(form.getWriterOauthProvider());
        article.setWriterOauthUidHash(form.getWriterOauthUidHash());
        article.setWriterName(form.getWriterName());
        article.setWriterContactEnc(form.getWriterContact());

        article.setTitle(form.getTitle());
        article.setContent(form.getContent());
        article.setClientIp(resolveClientIp());
        article.setComplaintNo(generateComplaintNo(master.getComplaintMasterId()));
        article.setStatus(ComplaintStatus.RECEIVED.name());

        // 답변 기한 자동 산출
        if (master.getAnswerDeadlineDays() != null) {
            article.setAnswerDueAt(LocalDateTime.now()
                .plusDays(master.getAnswerDeadlineDays()));
        }

        mapper.insert(article);
        auditLogger.write(AuditEvent.of("COMPLAINT_SUBMIT", "tb_complaint_article")
            .withTarget(article.getArticleId())
            .withAfter("{\"complaintNo\":" + JsonUtils.quote(article.getComplaintNo())
                     + ",\"masterCode\":" + JsonUtils.quote(master.getMasterCode()) + "}"));
        return article.getComplaintNo();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(ComplaintMaster master, String articleId, ComplaintArticleSaveForm form,
                       Authentication auth) {
        ComplaintArticle article = mapper.findById(articleId);
        if (article == null) throw new IllegalArgumentException("민원을 찾을 수 없습니다.");
        if (!isOwnerOrStaff(article, auth)) {
            throw new IllegalArgumentException("본인 민원만 수정할 수 있습니다.");
        }
        article.setCategoryId(notBlank(form.getCategoryId()) ? form.getCategoryId() : null);
        article.setWriterName(form.getWriterName());
        article.setWriterContactEnc(form.getWriterContact());
        article.setTitle(form.getTitle());
        article.setContent(form.getContent());

        if ("Y".equalsIgnoreCase(master.getFileYn())) {
            FileGroup group = ensureComplaintFileGroup(articleId, master.getSiteId());
            article.setFileGroupId(group.getFileGroupId());
            syncAttachments(group.getFileGroupId(), parseAttachments(form.getAttachmentsJson()));
        }

        mapper.update(article);
        auditLogger.write(AuditEvent.of("COMPLAINT_UPDATE", "tb_complaint_article")
            .withTarget(articleId)
            .withAfter("{\"title\":" + JsonUtils.quote(article.getTitle()) + "}"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void updateStatus(String articleId, String status) {
        mapper.updateStatus(articleId, status);
        auditLogger.write(AuditEvent.of("COMPLAINT_STATUS_CHANGE", "tb_complaint_article")
            .withTarget(articleId)
            .withAfter("{\"status\":" + JsonUtils.quote(status) + "}"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void close(String articleId, Authentication auth) {
        ComplaintArticle article = mapper.findById(articleId);
        if (article == null) throw new IllegalArgumentException("민원을 찾을 수 없습니다.");
        if (!isOwnerOrStaff(article, auth)) {
            throw new IllegalArgumentException("본인 민원만 종결할 수 있습니다.");
        }
        mapper.updateClosedAt(articleId);
        auditLogger.write(AuditEvent.of("COMPLAINT_CLOSE", "tb_complaint_article")
            .withTarget(articleId));
    }

    @Override
    public List<FileItem> listAttachments(String fileGroupId) {
        if (fileGroupId == null) return List.of();
        return fileMapper.findAllByGroupId(fileGroupId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void delete(String articleId) {
        mapper.softDelete(articleId);
        auditLogger.write(AuditEvent.of("COMPLAINT_DELETE", "tb_complaint_article")
            .withTarget(articleId));
    }

    // ── 접수번호 채번 — CPL-YYYYMMDD-NNNNNN ──────────────────────
    private String generateComplaintNo(String masterId) {
        String datePrefix = "CPL-" + LocalDate.now().format(DATE_FMT) + "-";
        int seq = mapper.countTodayByMaster(masterId, datePrefix) + 1;
        return String.format("%s%06d", datePrefix, seq);
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                return IpUtils.clientIp(req);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private FileGroup ensureComplaintFileGroup(String articleId, String siteId) {
        FileGroup g = fileGroupMapper.findByEntity("COMPLAINT", articleId);
        if (g != null) return g;
        FileGroup ng = new FileGroup();
        ng.setFileGroupId(UuidV7Generator.generate());
        ng.setEntityType("COMPLAINT");
        ng.setEntityId(articleId);
        ng.setSiteId(siteId);
        ng.setDownloadAuth("ROLE_MEMBER");
        ng.setDeleteYn("N");
        fileGroupMapper.insert(ng);
        return ng;
    }

    private void syncAttachments(String fileGroupId, List<String> keepFileIds) {
        if (fileGroupId == null) return;
        Set<String> keep = new HashSet<>(keepFileIds);
        List<FileItem> currentFiles = fileMapper.findAllByGroupId(fileGroupId);
        for (FileItem f : currentFiles) {
            if (!keep.contains(f.getFileId())) {
                fileMapper.softDelete(f.getFileId());
            }
        }
    }

    private static List<String> parseAttachments(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> ids = JSON.readValue(json, new TypeReference<>() {});
            return ids.stream()
                .filter(s -> s != null && s.matches(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
