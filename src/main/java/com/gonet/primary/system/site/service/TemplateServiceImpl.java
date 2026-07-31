package com.gonet.primary.system.site.service;

import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.system.site.dto.SiteContextChangedEvent;
import com.gonet.primary.system.site.dto.Template;
import com.gonet.primary.system.site.dto.TemplateSaveForm;
import com.gonet.primary.system.site.dto.TemplateSearch;
import com.gonet.primary.system.site.mapper.TemplateMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 템플릿 관리 서비스 구현 (eGov 표준).
 *
 * <p>2026-04-23c 부터 템플릿은 전역 카탈로그. CUD 시 어느 사이트가 참조하는지 알 수 없으므로
 * {@link SiteContextChangedEvent} 는 BULK (siteId=null) 로 발행 → 전체 캐시 무효화.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class TemplateServiceImpl extends EgovAbstractServiceImpl implements TemplateService {

    private final TemplateMapper mapper;
    private final ApplicationEventPublisher publisher;

    public TemplateServiceImpl(TemplateMapper mapper,
                               ApplicationEventPublisher publisher) {
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @Override
    public List<Template> search(TemplateSearch search) {
        return mapper.findList(search == null ? new TemplateSearch() : search);
    }

    @Override
    public int count(TemplateSearch search) {
        return mapper.countList(search == null ? new TemplateSearch() : search);
    }

    @Override
    public Template get(String templateId) {
        if (templateId == null || templateId.isBlank()) return null;
        return mapper.findById(templateId);
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(TemplateSaveForm form) {
        Objects.requireNonNull(form, "form");
        String code = form.getTemplateCode().trim().toUpperCase();
        if (mapper.existsByCode(code, null) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 템플릿 코드입니다: " + code);
        }

        Template t = new Template();
        t.setTemplateId(UuidV7Generator.generate("TPL"));
        t.setTemplateCode(code);
        t.setTemplateName(form.getTemplateName().trim());
        t.setLayoutPath(form.getLayoutPath().trim());
        t.setDesignMd(form.getDesignMd());
        t.setFileGroupId(form.getFileGroupId());
        t.setDescription(form.getDescription());
        t.setUseYn(form.getUseYn() == null ? "Y" : form.getUseYn());

        try {
            mapper.insert(t);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException("이미 존재하는 템플릿 코드입니다.", dup);
        }

        // 신규 템플릿은 아직 어느 사이트도 참조하지 않으므로 캐시 무효화 불필요
        return t.getTemplateId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(TemplateSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getTemplateId() == null || form.getTemplateId().isBlank()) {
            throw new IllegalArgumentException("templateId required");
        }
        Template existing = mapper.findById(form.getTemplateId());
        if (existing == null) {
            throw new IllegalArgumentException("템플릿을 찾을 수 없습니다: " + form.getTemplateId());
        }
        String code = form.getTemplateCode().trim().toUpperCase();
        if (mapper.existsByCode(code, existing.getTemplateId()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 템플릿 코드입니다: " + code);
        }

        existing.setTemplateCode(code);
        existing.setTemplateName(form.getTemplateName().trim());
        existing.setLayoutPath(form.getLayoutPath().trim());
        existing.setDesignMd(form.getDesignMd());
        existing.setFileGroupId(form.getFileGroupId());
        existing.setDescription(form.getDescription());
        existing.setUseYn(form.getUseYn() == null ? existing.getUseYn() : form.getUseYn());

        mapper.update(existing);
        // 전역 템플릿 변경 → 어느 사이트가 참조하는지 런타임에 미해결 → 전체 캐시 무효화
        publisher.publishEvent(SiteContextChangedEvent.of(null, SiteContextChangedEvent.Reason.BULK));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId required");
        }
        Template existing = mapper.findById(templateId);
        if (existing == null) {
            throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 템플릿입니다.");
        }

        int refs = mapper.countReferencingSites(templateId);
        if (refs > 0) {
            throw new IllegalArgumentException(
                "이 템플릿을 기본으로 사용 중인 사이트가 " + refs + " 개 있어 삭제할 수 없습니다. "
              + "먼저 사이트 상세에서 다른 템플릿으로 변경하세요.");
        }

        int affected = mapper.softDelete(templateId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + templateId);
        }

        // 전역 템플릿 삭제 → 전체 캐시 무효화
        publisher.publishEvent(SiteContextChangedEvent.of(null, SiteContextChangedEvent.Reason.BULK));
    }
}
