package com.gonet.primary.board.category.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.board.category.dto.BbsCategory;
import com.gonet.primary.board.category.dto.BbsCategorySaveForm;
import com.gonet.primary.board.category.mapper.BbsCategoryMapper;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.mapper.BbsMasterMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 게시판 카테고리 서비스 구현 — eGov {@link EgovAbstractServiceImpl} 상속.
 *
 * <p>UNIQUE 충돌 (bbs_master_id, category_code) 검증 + 삭제 가드(소속 글 존재 시 차단)
 * + 감사 이벤트 발행.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class BoardCategoryServiceImpl extends EgovAbstractServiceImpl
        implements BoardCategoryService {

    private static final Logger log = LoggerFactory.getLogger(BoardCategoryServiceImpl.class);

    private final BbsCategoryMapper categoryMapper;
    private final BbsMasterMapper   masterMapper;
    private final AuditLogger       auditLogger;

    public BoardCategoryServiceImpl(BbsCategoryMapper categoryMapper,
                                     BbsMasterMapper masterMapper,
                                     AuditLogger auditLogger) {
        this.categoryMapper = categoryMapper;
        this.masterMapper   = masterMapper;
        this.auditLogger    = auditLogger;
    }

    @Override
    public List<BbsCategory> listActive(String bbsMasterId) {
        if (bbsMasterId == null || bbsMasterId.isBlank()) return List.of();
        return categoryMapper.findActiveByBbs(bbsMasterId);
    }

    @Override
    public List<BbsCategory> listAll(String bbsMasterId) {
        if (bbsMasterId == null || bbsMasterId.isBlank()) return List.of();
        return categoryMapper.findListByBbs(bbsMasterId);
    }

    @Override
    public BbsCategory get(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return null;
        return categoryMapper.findById(categoryId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(BbsCategorySaveForm form) {
        Objects.requireNonNull(form, "form");
        BbsMaster master = masterMapper.findById(form.getBbsMasterId());
        if (master == null) {
            throw new ResourceNotFoundException("게시판을 찾을 수 없습니다.");
        }
        if (categoryMapper.existsByCode(form.getBbsMasterId(),
                                         form.getCategoryCode(), null) > 0) {
            throw new IllegalArgumentException(
                "이미 사용 중인 카테고리 코드입니다: " + form.getCategoryCode());
        }

        BbsCategory c = new BbsCategory();
        c.setCategoryId(UuidV7Generator.generate("BCT"));
        c.setBbsMasterId(form.getBbsMasterId());
        c.setCategoryCode(form.getCategoryCode().trim());
        c.setCategoryName(form.getCategoryName().trim());
        c.setSortOrder(form.getSortOrder());
        c.setUseYn(normalizeYn(form.getUseYn(), "Y"));
        c.setDeleteYn("N");
        categoryMapper.insert(c);

        auditLogger.write(AuditEvent.of("BBS_CATEGORY_CREATE", "tb_bbs_category")
            .withTarget(c.getCategoryId())
            .withAfter("{\"bbsMasterId\":" + JsonUtils.quote(c.getBbsMasterId())
                + ",\"categoryCode\":" + JsonUtils.quote(c.getCategoryCode())
                + ",\"categoryName\":" + JsonUtils.quote(c.getCategoryName()) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_CATEGORY_CREATE_OK id={} code={}",
            c.getCategoryId(), c.getCategoryCode());
        return c.getCategoryId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(BbsCategorySaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getCategoryId() == null || form.getCategoryId().isBlank()) {
            throw new IllegalArgumentException("categoryId required");
        }
        BbsCategory existing = categoryMapper.findById(form.getCategoryId());
        if (existing == null) {
            throw new ResourceNotFoundException("카테고리를 찾을 수 없습니다.");
        }
        // bbs_master_id 변경은 비허용 — 다른 게시판으로의 카테고리 이동은 신규 작성으로 유도
        if (!existing.getBbsMasterId().equals(form.getBbsMasterId())) {
            throw new IllegalArgumentException("게시판은 변경할 수 없습니다.");
        }
        if (categoryMapper.existsByCode(existing.getBbsMasterId(),
                                         form.getCategoryCode(),
                                         existing.getCategoryId()) > 0) {
            throw new IllegalArgumentException(
                "이미 사용 중인 카테고리 코드입니다: " + form.getCategoryCode());
        }

        existing.setCategoryCode(form.getCategoryCode().trim());
        existing.setCategoryName(form.getCategoryName().trim());
        existing.setSortOrder(form.getSortOrder());
        existing.setUseYn(normalizeYn(form.getUseYn(), "Y"));
        categoryMapper.update(existing);

        auditLogger.write(AuditEvent.of("BBS_CATEGORY_UPDATE", "tb_bbs_category")
            .withTarget(existing.getCategoryId())
            .withAfter("{\"categoryCode\":" + JsonUtils.quote(existing.getCategoryCode())
                + ",\"categoryName\":" + JsonUtils.quote(existing.getCategoryName())
                + ",\"sortOrder\":" + existing.getSortOrder()
                + ",\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_CATEGORY_UPDATE_OK id={}", existing.getCategoryId());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String categoryId, boolean active) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId required");
        }
        BbsCategory existing = categoryMapper.findById(categoryId);
        if (existing == null) {
            throw new ResourceNotFoundException("카테고리를 찾을 수 없습니다.");
        }
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) return;
        categoryMapper.updateUseYn(categoryId, next);

        auditLogger.write(AuditEvent.of("BBS_CATEGORY_USE_TOGGLE", "tb_bbs_category")
            .withTarget(categoryId)
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId required");
        }
        BbsCategory existing = categoryMapper.findById(categoryId);
        if (existing == null) {
            throw new ResourceNotFoundException("이미 삭제되었거나 존재하지 않는 카테고리입니다.");
        }
        int activeArticles = categoryMapper.countActiveArticles(categoryId);
        if (activeArticles > 0) {
            throw new IllegalArgumentException(
                "이 카테고리에 속한 게시글이 " + activeArticles + "건 존재하여 삭제할 수 없습니다. "
              + "먼저 게시글의 카테고리를 변경하거나 삭제해주세요.");
        }
        categoryMapper.softDelete(categoryId);

        auditLogger.write(AuditEvent.of("BBS_CATEGORY_DELETE", "tb_bbs_category")
            .withTarget(categoryId)
            .withBefore("{\"categoryCode\":" + JsonUtils.quote(existing.getCategoryCode())
                + ",\"categoryName\":" + JsonUtils.quote(existing.getCategoryName()) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_CATEGORY_DELETE_OK id={}", categoryId);
    }

    private static String normalizeYn(String raw, String fallback) {
        if (raw == null) return fallback;
        String v = raw.trim().toUpperCase();
        return ("Y".equals(v) || "N".equals(v)) ? v : fallback;
    }
}
