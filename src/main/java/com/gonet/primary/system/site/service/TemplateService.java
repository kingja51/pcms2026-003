package com.gonet.primary.system.site.service;

import com.gonet.primary.system.site.dto.SiteContextChangedEvent;
import com.gonet.primary.system.site.dto.Template;
import com.gonet.primary.system.site.dto.TemplateSaveForm;
import com.gonet.primary.system.site.dto.TemplateSearch;

import java.util.List;

/**
 * 템플릿 관리 서비스 — 관리자 도메인.
 *
 * <p>eGov 호환성: 인터페이스 + {@link TemplateServiceImpl} (EgovAbstractServiceImpl 상속).
 * <p>모든 CUD 는 {@link SiteContextChangedEvent}(TEMPLATE_CHANGED) 발행 → Caffeine 캐시 무효화.
 */
public interface TemplateService {

    List<Template> search(TemplateSearch search);

    int count(TemplateSearch search);

    Template get(String templateId);

    /** 반환: 생성된 template_id (UUID v7) */
    String create(TemplateSaveForm form);

    void update(TemplateSaveForm form);

    /**
     * 소프트 삭제. 이 템플릿을 default 로 가리키는 사이트가 있으면 예외.
     * 먼저 사이트 상세에서 다른 템플릿으로 변경 필요.
     */
    void softDelete(String templateId);
}
