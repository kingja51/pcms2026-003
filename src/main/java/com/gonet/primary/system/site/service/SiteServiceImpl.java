package com.gonet.primary.system.site.service;

import com.gonet.common.util.UuidV7Generator;
import com.gonet.primary.system.menu.service.MenuService;
import com.gonet.primary.system.site.dto.*;
import com.gonet.primary.system.site.mapper.SiteMapper;
import com.gonet.primary.system.site.mapper.SiteTemplateMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 사이트 관리 서비스 구현 (eGov 표준 {@link EgovAbstractServiceImpl} 상속).
 *
 * <p>공통 트랜잭션: readOnly=true (read 기본), CUD 메서드에만 readOnly=false.
 * <p>캐시 무효화는 이벤트 발행 방식 —
 * {@link SiteContextChangedListener} 가
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 로 수신.
 */
@Service
@Transactional(readOnly = true, transactionManager =
    com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
public class SiteServiceImpl extends EgovAbstractServiceImpl implements SiteService {

    private static final Logger log = LoggerFactory.getLogger(SiteServiceImpl.class);

    private final SiteMapper mapper;
    private final SiteTemplateMapper templateMapper;
    private final MenuService menuService;
    private final ApplicationEventPublisher publisher;

    public SiteServiceImpl(SiteMapper mapper,
                           SiteTemplateMapper templateMapper,
                           MenuService menuService,
                           ApplicationEventPublisher publisher) {
        this.mapper = mapper;
        this.templateMapper = templateMapper;
        this.menuService = menuService;
        this.publisher = publisher;
    }

    @Override
    public List<Site> search(SiteSearch search) {
        return mapper.findList(search == null ? new SiteSearch() : search);
    }

    @Override
    public int count(SiteSearch search) {
        return mapper.countList(search == null ? new SiteSearch() : search);
    }

    @Override
    public Site get(String siteId) {
        if (siteId == null || siteId.isBlank()) return null;
        return mapper.findById(siteId);
    }

    @Override
    public List<TemplateInfo> listTemplates(String siteId) {
        // 2026-04-23c 부터 템플릿은 전역 카탈로그 — siteId 는 하위 호환 시그니처로 무시
        return templateMapper.findAllActive();
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(SiteSaveForm form) {
        Objects.requireNonNull(form, "form");
        validateDuplicates(form.getSiteCode(), form.getDomain(), null);
        validateTemplateExists(form.getDefaultTemplateId());

        Site s = new Site();
        s.setSiteId(UuidV7Generator.generate("SIT"));
        s.setSiteCode(form.getSiteCode().trim());
        s.setSiteName(form.getSiteName().trim());
        s.setDomain(blankToNull(form.getDomain()));
        s.setDefaultLang(form.getDefaultLang());
        s.setDefaultTemplateId(blankToNull(form.getDefaultTemplateId()));
        s.setDescription(form.getDescription());
        s.setHeadMeta(blankToNull(form.getHeadMeta()));
        s.setCopyright(blankToNull(form.getCopyright()));
        s.setTheme(normalizeTheme(form.getTheme()));
        s.setUseYn(form.getUseYn() == null ? "Y" : form.getUseYn());

        try {
            mapper.insert(s);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException("이미 존재하는 사이트 코드 또는 도메인입니다.", dup);
        }

        // I-012 — 신규 사이트는 루트 메뉴를 기본 1건 자동 seed. 같은 레이아웃을 공유하는 신규 사이트가
        // 빈 내비로 렌더되는 것을 방지. 루트 이름은 사이트명을 사용 (미입력 시 "홈").
        String rootMenuId = menuService.seedRootMenuIfAbsent(s.getSiteId(), s.getSiteName());
        if (rootMenuId != null) {
            log.info("===SITE_CREATE root menu seeded siteId={} menuId={}", s.getSiteId(), rootMenuId);
        }

        publisher.publishEvent(
            new SiteContextChangedEvent(s.getSiteId(), SiteContextChangedEvent.Reason.SITE_UPDATED));
        return s.getSiteId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(SiteSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getSiteId() == null || form.getSiteId().isBlank()) {
            throw new IllegalArgumentException("siteId required");
        }
        Site existing = mapper.findById(form.getSiteId());
        if (existing == null) {
            throw new IllegalArgumentException("사이트를 찾을 수 없습니다: " + form.getSiteId());
        }
        validateDuplicates(form.getSiteCode(), form.getDomain(), form.getSiteId());
        validateTemplateExists(form.getDefaultTemplateId());

        existing.setSiteCode(form.getSiteCode().trim());
        existing.setSiteName(form.getSiteName().trim());
        existing.setDomain(blankToNull(form.getDomain()));
        existing.setDefaultLang(form.getDefaultLang());
        existing.setDefaultTemplateId(blankToNull(form.getDefaultTemplateId()));
        existing.setDescription(form.getDescription());
        existing.setHeadMeta(blankToNull(form.getHeadMeta()));
        existing.setCopyright(blankToNull(form.getCopyright()));
        existing.setTheme(normalizeTheme(form.getTheme()));
        existing.setUseYn(form.getUseYn() == null ? existing.getUseYn() : form.getUseYn());

        mapper.update(existing);
        publisher.publishEvent(
            new SiteContextChangedEvent(existing.getSiteId(), SiteContextChangedEvent.Reason.SITE_UPDATED));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void changeDefaultTemplate(String siteId, String templateId) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("siteId required");
        }
        Site site = mapper.findById(siteId);
        if (site == null) {
            throw new IllegalArgumentException("사이트를 찾을 수 없습니다: " + siteId);
        }
        validateTemplateExists(templateId);

        mapper.updateDefaultTemplate(siteId, blankToNull(templateId));
        publisher.publishEvent(
            new SiteContextChangedEvent(siteId, SiteContextChangedEvent.Reason.TEMPLATE_CHANGED));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("siteId required");
        }
        int affected = mapper.softDelete(siteId);
        if (affected == 0) {
            throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 사이트입니다: " + siteId);
        }
        publisher.publishEvent(
            new SiteContextChangedEvent(siteId, SiteContextChangedEvent.Reason.SITE_UPDATED));
    }

    // ------------------------------------------------------------------
    // 검증 헬퍼
    // ------------------------------------------------------------------

    private void validateDuplicates(String siteCode, String domain, String excludeSiteId) {
        if (siteCode != null && !siteCode.isBlank()) {
            if (mapper.existsByCode(siteCode.trim().toUpperCase(), excludeSiteId) > 0) {
                throw new IllegalArgumentException("이미 사용 중인 사이트 코드입니다: " + siteCode);
            }
        }
        if (domain != null && !domain.isBlank()) {
            if (mapper.existsByDomain(domain.trim(), excludeSiteId) > 0) {
                throw new IllegalArgumentException("이미 등록된 도메인입니다: " + domain);
            }
        }
    }

    /**
     * 2026-04-23c 부터 템플릿은 전역 카탈로그 — 존재·활성 여부만 검증.
     */
    private void validateTemplateExists(String templateId) {
        if (templateId == null || templateId.isBlank()) return;
        TemplateInfo t = templateMapper.findById(templateId);
        if (t == null) {
            throw new IllegalArgumentException("템플릿을 찾을 수 없거나 비활성 상태입니다: " + templateId);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * 테마 입력 정규화 — 저장 canonical 은 "theme-" 접두 포함 소문자 클래스명.
     * 빈값은 null (템플릿 기본 브랜드), "navy" 처럼 접두 생략 입력은 "theme-navy" 로 보정.
     */
    private static String normalizeTheme(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim().toLowerCase();
        return t.startsWith("theme-") ? t : "theme-" + t;
    }
}
