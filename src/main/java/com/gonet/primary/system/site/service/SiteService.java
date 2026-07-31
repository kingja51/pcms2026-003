package com.gonet.primary.system.site.service;

import com.gonet.primary.system.site.dto.*;

import java.util.List;

/**
 * 사이트 관리 서비스 — 관리자 도메인.
 *
 * <p>eGov 호환성: 인터페이스 + {@link SiteServiceImpl} (EgovAbstractServiceImpl 상속).
 * Controller 는 본 인터페이스만 주입받음 (DAO 직접호출 금지).
 */
public interface SiteService {

    /** 사이트 목록 조회 (페이징) */
    List<Site> search(SiteSearch search);

    /** 조건에 맞는 총 개수 (페이지 계산용) */
    int count(SiteSearch search);

    /** 단건 조회 — 관리자 상세 화면용. 없으면 null */
    Site get(String siteId);

    /** 사이트 내 활성 템플릿 목록 — 선택 UI */
    List<TemplateInfo> listTemplates(String siteId);

    /**
     * 신규 등록. 반환: 생성된 site_id (UUID v7).
     * {@link SiteContextChangedEvent} 발행.
     */
    String create(SiteSaveForm form);

    /** 수정. {@link SiteContextChangedEvent} 발행. */
    void update(SiteSaveForm form);

    /**
     * 기본 템플릿 변경 — 사이트 상세 화면 "템플릿 선택" UI 의 핵심.
     * {@link SiteContextChangedEvent} (TEMPLATE_CHANGED) 발행.
     */
    void changeDefaultTemplate(String siteId, String templateId);

    /** 소프트 삭제 (delete_yn='Y'). {@link SiteContextChangedEvent} 발행. */
    void softDelete(String siteId);
}
