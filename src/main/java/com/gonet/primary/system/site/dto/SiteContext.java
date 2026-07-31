package com.gonet.primary.system.site.dto;

import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 요청 처리 전반에서 사용되는 사이트/템플릿/메뉴 통합 컨텍스트.
 *
 * <p>구성:
 * <ul>
 *   <li>{@link #site}     : 사이트 기본 정보 (site_id / 코드 / 도메인 / 언어)</li>
 *   <li>{@link #template} : 사이트 기본 템플릿 (layoutPath 가 핵심) — null 가능</li>
 *   <li>{@link #menuTree} : 최상위 메뉴 목록 (각 노드는 children 포함, depth=1)</li>
 *   <li>{@link #loadedAt} : 캐시 진입 시각 (디버깅/메트릭)</li>
 * </ul>
 *
 * <p>캐시: Caffeine({@code siteContext}, key = siteId). TTL 10분(application.yml).
 * 관리자 CUD 에서 {@code SiteContextChangedEvent} 발행 시 즉시 무효화.
 *
 * <p>Controller 에서는 {@code HttpServletRequest.getAttribute("siteContext")}
 * 또는 {@code SiteContextHolder.get()} 으로 참조.
 */
@Getter
public class SiteContext implements Serializable {

    public static final String REQUEST_ATTR = "siteContext";

    private final SiteSummary    site;
    private final TemplateInfo   template;   // nullable (template_id NULL 시)
    private final List<MenuNode> menuTree;
    private final Instant        loadedAt;
    /** 현재 요청의 메뉴 ID — 캐시 무관, 요청 단위 부가 정보. nullable. */
    private final String         menuId;

    public SiteContext(SiteSummary site, TemplateInfo template, List<MenuNode> menuTree) {
        this(site, template, menuTree, null);
    }

    public SiteContext(SiteSummary site, TemplateInfo template,
                        List<MenuNode> menuTree, String menuId) {
        this.site      = Objects.requireNonNull(site, "site must not be null");
        this.template  = template;
        this.menuTree  = (menuTree == null) ? Collections.emptyList()
                                            : Collections.unmodifiableList(menuTree);
        this.loadedAt  = Instant.now();
        this.menuId    = menuId;
    }

    /**
     * 캐시된 컨텍스트에 menuId 만 덧입혀 새 인스턴스 반환 (캐시 원본은 불변 유지).
     * 동일 menuId 면 자기 자신을 반환 (객체 재생성 회피).
     */
    public SiteContext withMenuId(String menuId) {
        if (Objects.equals(this.menuId, menuId)) return this;
        return new SiteContext(this.site, this.template, this.menuTree, menuId);
    }

    public String getSiteId()    { return site.getSiteId(); }
    public String getSiteCode()  { return site.getSiteCode(); }
    /** tb_site.theme 원본값 (theme-*). 빈값/NULL = 템플릿 기본 브랜드.
     *  화이트리스트 검증·정규화는 SiteContextModelAdvice(themeClass 주입)가 담당. */
    public String getTheme()     { return site.getTheme(); }
    public String getMenuId()    { return menuId; }
    public String getLayoutPath() {
        return template != null ? template.getLayoutPath() : null;
    }
    public boolean hasTemplate() {
        return template != null;
    }
}
