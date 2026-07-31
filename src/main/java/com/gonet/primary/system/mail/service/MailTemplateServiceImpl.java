package com.gonet.primary.system.mail.service;

import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.cache.CacheConfig;
import com.gonet.primary.system.mail.dto.MailTemplate;
import com.gonet.primary.system.mail.dto.MailTemplateSaveForm;
import com.gonet.primary.system.mail.dto.MailTemplateSearch;
import com.gonet.primary.system.mail.mapper.MailTemplateMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 메일 템플릿 CRUD + 활성 조회 캐시.
 *
 * <p>CUD 시 {@link CacheConfig#MAIL_TEMPLATE} 전역 무효화 (코드 재명명·복사 고려).
 */
@Service
@Transactional(readOnly = true, transactionManager =
    com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MailTemplateServiceImpl extends EgovAbstractServiceImpl implements MailTemplateService {

    private final MailTemplateMapper mapper;

    public MailTemplateServiceImpl(MailTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override public List<MailTemplate> search(MailTemplateSearch s) {
        return mapper.findList(normalize(s));
    }

    @Override public int count(MailTemplateSearch s) {
        return mapper.countList(normalize(s));
    }

    @Override
    public MailTemplate get(String id) {
        if (id == null || id.isBlank()) return null;
        return mapper.findById(id);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.MAIL_TEMPLATE, key = "'code:' + #code", unless = "#result == null")
    public MailTemplate getByCodeActive(String code) {
        if (code == null || code.isBlank()) return null;
        return mapper.findActiveByCode(code);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.MAIL_TEMPLATE, allEntries = true)
    public String create(MailTemplateSaveForm form) {
        Objects.requireNonNull(form, "form");
        String code = form.getTemplateCode().trim().toUpperCase();
        if (mapper.existsByCode(code, null) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 템플릿 코드입니다: " + code);
        }
        MailTemplate t = new MailTemplate();
        t.setMailTemplateId(UuidV7Generator.generate("MTP"));
        t.setTemplateCode(code);
        t.setTemplateName(form.getTemplateName().trim());
        t.setSubject(form.getSubject().trim());
        t.setBodyHtml(form.getBodyHtml());
        t.setSenderEmail(blankToNull(form.getSenderEmail()));
        t.setSenderName(blankToNull(form.getSenderName()));
        t.setDescription(blankToNull(form.getDescription()));
        t.setVariablesHint(blankToNull(form.getVariablesHint()));
        t.setUseYn(form.getUseYn() == null ? "Y" : form.getUseYn());
        try {
            mapper.insert(t);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException("이미 존재하는 템플릿 코드입니다.", dup);
        }
        return t.getMailTemplateId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.MAIL_TEMPLATE, allEntries = true)
    public void update(MailTemplateSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getMailTemplateId() == null || form.getMailTemplateId().isBlank()) {
            throw new IllegalArgumentException("mailTemplateId required");
        }
        MailTemplate existing = mapper.findById(form.getMailTemplateId());
        if (existing == null) throw new IllegalArgumentException("템플릿을 찾을 수 없습니다.");

        String code = form.getTemplateCode().trim().toUpperCase();
        if (mapper.existsByCode(code, form.getMailTemplateId()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 템플릿 코드입니다: " + code);
        }
        existing.setTemplateCode(code);
        existing.setTemplateName(form.getTemplateName().trim());
        existing.setSubject(form.getSubject().trim());
        existing.setBodyHtml(form.getBodyHtml());
        existing.setSenderEmail(blankToNull(form.getSenderEmail()));
        existing.setSenderName(blankToNull(form.getSenderName()));
        existing.setDescription(blankToNull(form.getDescription()));
        existing.setVariablesHint(blankToNull(form.getVariablesHint()));
        existing.setUseYn(form.getUseYn());
        mapper.update(existing);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.MAIL_TEMPLATE, allEntries = true)
    public void softDelete(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("mailTemplateId required");
        }
        int n = mapper.softDelete(id);
        if (n == 0) throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 템플릿입니다.");
    }

    private static MailTemplateSearch normalize(MailTemplateSearch s) {
        return s == null ? new MailTemplateSearch() : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
