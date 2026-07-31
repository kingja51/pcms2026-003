package com.gonet.primary.content.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditContext;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.content.dto.Content;
import com.gonet.primary.content.dto.ContentHistory;
import com.gonet.primary.content.dto.ContentSaveForm;
import com.gonet.primary.content.dto.ContentSearch;
import com.gonet.primary.content.dto.ContentStatus;
import com.gonet.primary.content.mapper.ContentMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 콘텐츠 서비스 구현 — eGov {@link EgovAbstractServiceImpl} 상속.
 *
 * <p>핵심 책임:
 * <ul>
 *   <li>CRUD + slug 중복 검증</li>
 *   <li>UPDATE 시 자동 버전 이력 (기존 스냅샷 → tb_content_history, version_no += 1)</li>
 *   <li>상태 전환 (DRAFT ↔ REVIEW ↔ APPROVED ↔ PUBLISHED ↔ UNPUBLISHED) + 허용 전이 검증</li>
 *   <li>감사 이벤트 발행 (CONTENT_CREATE/UPDATE/DELETE/STATUS_CHANGE)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class ContentServiceImpl extends EgovAbstractServiceImpl implements ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentServiceImpl.class);

    private final ContentMapper        mapper;
    private final AuditLogger          auditLogger;

    public ContentServiceImpl(ContentMapper mapper,
                               AuditLogger auditLogger) {
        this.mapper             = mapper;
        this.auditLogger        = auditLogger;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override
    public List<Content> search(ContentSearch search) {
        return mapper.findList(search == null ? new ContentSearch() : search);
    }

    @Override
    public int count(ContentSearch search) {
        return mapper.countList(search == null ? new ContentSearch() : search);
    }

    @Override
    public Content get(String contentId) {
        if (contentId == null || contentId.isBlank()) return null;
        return mapper.findById(contentId);
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(ContentSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getSiteId() == null || form.getSiteId().isBlank()) {
            throw new IllegalArgumentException("사이트를 선택하세요.");
        }
        validateSlug(form.getSiteId(), form.getSlug(), null);

        Content c = new Content();
        c.setContentId(UuidV7Generator.generate("CNT"));
        c.setSiteId(form.getSiteId());
        c.setMenuId(blankToNull(form.getMenuId()));
        c.setTitle(form.getTitle().trim());
        c.setSlug(blankToNull(form.getSlug()));
        c.setBody(form.getBody());
        c.setOriginalContent(form.getOriginalContent());
        c.setSummary(form.getSummary());
        c.setMetaKeywords(form.getMetaKeywords());
        c.setMetaDescription(form.getMetaDescription());
        c.setStatus(ContentStatus.DRAFT.name());
        c.setViewCount(0L);
        c.setVersionNo(1);

        try {
            mapper.insert(c);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException("이미 존재하는 slug 입니다.", dup);
        }

        auditLogger.write(AuditEvent.of("CONTENT_CREATE", "tb_content")
            .withTarget(c.getContentId())
            .withAfter("{\"title\":" + JsonUtils.quote(c.getTitle())
                + ",\"slug\":" + JsonUtils.quote(c.getSlug())
                + ",\"status\":\"DRAFT\"}")
            .withResult("SUCCESS"));

        log.info("===CONTENT_CREATE_OK contentId={} siteId={} slug={}",
            c.getContentId(), c.getSiteId(), c.getSlug());

        return c.getContentId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(ContentSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getContentId() == null || form.getContentId().isBlank()) {
            throw new IllegalArgumentException("contentId required");
        }
        Content existing = mapper.findById(form.getContentId());
        if (existing == null) {
            throw new ResourceNotFoundException("콘텐츠를 찾을 수 없습니다.");
        }
        validateSlug(existing.getSiteId(), form.getSlug(), existing.getContentId());

        snapshotToHistory(existing, form.getChangeNote());

        existing.setMenuId(blankToNull(form.getMenuId()));
        existing.setTitle(form.getTitle().trim());
        existing.setSlug(blankToNull(form.getSlug()));
        existing.setBody(form.getBody());
        existing.setOriginalContent(form.getOriginalContent());
        existing.setSummary(form.getSummary());
        existing.setMetaKeywords(form.getMetaKeywords());
        existing.setMetaDescription(form.getMetaDescription());
        existing.setVersionNo(existing.getVersionNo() + 1);

        mapper.update(existing);

        auditLogger.write(AuditEvent.of("CONTENT_UPDATE", "tb_content")
            .withTarget(existing.getContentId())
            .withAfter("{\"versionNo\":" + existing.getVersionNo()
                + ",\"title\":" + JsonUtils.quote(existing.getTitle())
                + ",\"changeNote\":" + JsonUtils.quote(form.getChangeNote()) + "}")
            .withResult("SUCCESS"));

        log.info("===CONTENT_UPDATE_OK contentId={} version={}",
            existing.getContentId(), existing.getVersionNo());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void updateBody(String contentId, String body, String changeNote) {
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException("contentId required");
        }
        Content existing = mapper.findById(contentId);
        if (existing == null) {
            throw new ResourceNotFoundException("콘텐츠를 찾을 수 없습니다.");
        }
        // 동일 본문이면 no-op (version 증가/이력 적재 방지)
        String oldBody = existing.getBody() == null ? "" : existing.getBody();
        String newBody = body == null ? "" : body;
        if (oldBody.equals(newBody)) {
            log.info("===CONTENT_UPDATE_BODY skip contentId={} reason=unchanged", contentId);
            return;
        }

        snapshotToHistory(existing, changeNote);

        existing.setBody(newBody);
        existing.setVersionNo(existing.getVersionNo() + 1);
        mapper.update(existing);

        auditLogger.write(AuditEvent.of("CONTENT_UPDATE_BODY", "tb_content")
            .withTarget(contentId)
            .withAfter("{\"versionNo\":" + existing.getVersionNo()
                + ",\"bodyLen\":" + newBody.length()
                + ",\"changeNote\":" + JsonUtils.quote(changeNote) + "}")
            .withResult("SUCCESS"));

        log.info("===CONTENT_UPDATE_BODY ok contentId={} version={} bodyLen={}",
            contentId, existing.getVersionNo(), newBody.length());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String contentId) {
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException("contentId required");
        }
        Content existing = mapper.findById(contentId);
        if (existing == null) {
            throw new ResourceNotFoundException("이미 삭제되었거나 존재하지 않는 콘텐츠입니다.");
        }
        if (ContentStatus.safeParse(existing.getStatus()) == ContentStatus.PUBLISHED) {
            throw new IllegalArgumentException(
                "게시 중인 콘텐츠는 삭제할 수 없습니다. 먼저 게시 중단(UNPUBLISHED) 처리하세요.");
        }
        int affected = mapper.softDelete(contentId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + contentId);
        }

        auditLogger.write(AuditEvent.of("CONTENT_DELETE", "tb_content")
            .withTarget(contentId)
            .withBefore("{\"title\":" + JsonUtils.quote(existing.getTitle())
                + ",\"status\":" + JsonUtils.quote(existing.getStatus()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void changeStatus(String contentId, ContentStatus target) {
        Objects.requireNonNull(target, "target status");
        Content existing = mapper.findById(contentId);
        if (existing == null) {
            throw new ResourceNotFoundException("콘텐츠를 찾을 수 없습니다.");
        }
        ContentStatus current = ContentStatus.safeParse(existing.getStatus());
        current.assertCanTransitionTo(target);

        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime publishedAt = existing.getPublishedAt();
        LocalDateTime unpublishAt = existing.getUnpublishAt();

        if (target == ContentStatus.PUBLISHED) {
            publishedAt = now;
            unpublishAt = null;
        } else if (target == ContentStatus.UNPUBLISHED) {
            unpublishAt = now;
        }

        mapper.updateStatus(contentId, target.name(), publishedAt, unpublishAt);

        auditLogger.write(AuditEvent.of("CONTENT_STATUS_CHANGE", "tb_content")
            .withTarget(contentId)
            .withBefore("{\"status\":\"" + current + "\"}")
            .withAfter("{\"status\":\"" + target + "\"}")
            .withResult("SUCCESS"));

        log.info("===CONTENT_STATUS_OK contentId={} {} -> {}", contentId, current, target);
    }

    // ------------------------------------------------------------------
    // 이력 조회
    // ------------------------------------------------------------------

    @Override
    public List<ContentHistory> listHistory(String contentId) {
        if (contentId == null || contentId.isBlank()) return List.of();
        return mapper.findHistoryByContentId(contentId);
    }

    @Override
    public ContentHistory getHistory(String contentHistoryId) {
        if (contentHistoryId == null || contentHistoryId.isBlank()) return null;
        return mapper.findHistory(contentHistoryId);
    }

    // ------------------------------------------------------------------
    // 내부 헬퍼
    // ------------------------------------------------------------------

    private void snapshotToHistory(Content current, String changeNote) {
        ContentHistory h = new ContentHistory();
        h.setContentHistoryId(UuidV7Generator.generate("CHS"));
        h.setContentId(current.getContentId());
        h.setVersionNo(current.getVersionNo());
        h.setTitle(current.getTitle());
        h.setBody(current.getBody());
        h.setSummary(current.getSummary());
        h.setChangedBy(AuditContext.userId());
        h.setChangeNote(changeNote);
        mapper.insertHistory(h);
    }

    private void validateSlug(String siteId, String slug, String excludeContentId) {
        if (slug == null || slug.isBlank()) return;
        if (mapper.existsBySlug(siteId, slug.trim(), excludeContentId) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 slug 입니다: " + slug);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
