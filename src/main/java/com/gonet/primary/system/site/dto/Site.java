package com.gonet.primary.system.site.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_site 1건 — CRUD 대상 풀 엔티티.
 *
 * <p>{@link SiteSummary} 는 {@code SiteContext} 로드용 경량 DTO 로 별도 유지.
 * 관리자 화면 / 엑셀 다운로드에서는 감사컬럼을 포함한 이 클래스 사용.
 */
@Getter
@Setter
public class Site extends BaseEntity implements SoftDeletable, UseFlagged {

    private String siteId;
    private String siteCode;
    private String siteName;
    private String domain;
    private String defaultLang;
    private String defaultTemplateId;
    private String description;
    /** <head> 삽입용 raw HTML (meta/link/script 등). 2026-04-23e DDL. */
    private String headMeta;
    /** footer copyright 표시용 raw HTML/텍스트. 2026-04-23e DDL. */
    private String copyright;
    /** KRDS 테마 클래스 (theme-*). 빈값/NULL = 템플릿 기본 브랜드. 2026-07-07 DDL. */
    private String theme;
    private String useYn;
    private String deleteYn;

    // 조회 시 JOIN 으로 채워지는 표시용 필드 (tb_template.template_name)
    private String defaultTemplateName;
}
