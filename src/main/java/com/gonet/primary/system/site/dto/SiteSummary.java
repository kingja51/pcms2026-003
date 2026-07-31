package com.gonet.primary.system.site.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * tb_site 경량 조회 DTO (SiteContext 구성 요소).
 *
 * <p>감사컬럼은 SiteContext 에서 직접 쓰이지 않으므로 제외 — BaseEntity 미상속.
 */
@Getter
@Setter
public class SiteSummary {

    private String siteId;
    private String siteCode;
    private String siteName;
    private String domain;
    private String defaultLang;
    private String defaultTemplateId;  // 2026-04-19 DDL 추가 컬럼
    /** 사이트 설명 — 랜딩(samples/home 등) 히어로 문구로 사용. 2026-07-07 추가. */
    private String description;
    /** <head> 삽입용 HTML 조각. 2026-04-23e DDL. */
    private String headMeta;
    /** footer copyright HTML/텍스트. 2026-04-23e DDL. */
    private String copyright;
    /** KRDS 테마 클래스 (theme-*). 빈값/NULL = 템플릿 기본 브랜드. 2026-07-07 DDL. */
    private String theme;
    private String useYn;
    private String layoutPath;
}
