package com.gonet.primary.system.site.service;

import com.gonet.config.cache.CacheConfig;
import com.gonet.primary.system.site.dto.*;
import com.gonet.primary.system.site.mapper.SiteContextMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SiteContext 로드·캐시 서비스.
 *
 * <p>단일 진입점 {@link #getContext(String)} 에서 site + template + menu 를 병합하여
 * {@link SiteContext} 를 구성, Caffeine 캐시에 적재.
 *
 * <p>관리자 CUD 시 {@link SiteContextChangedEvent} 를 발행하면
 * {@link SiteContextChangedListener} 가 {@link #evict(String)} 을 호출.
 */
@Service
public class SiteContextService {

    private static final Logger log = LoggerFactory.getLogger(SiteContextService.class);

    private final SiteContextMapper mapper;

    public SiteContextService(SiteContextMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 캐시 우선 조회. 미스 시 DB 에서 site/template/menu 3쿼리로 로드 후 트리 조립.
     *
     * @param siteId 사이트 UUID
     * @return SiteContext (사이트가 없거나 비활성이면 null)
     */
    @Cacheable(value = CacheConfig.SITE_CONTEXT, key = "#siteId", unless = "#result == null")
    public SiteContext getContext(String siteId) {
        log.info("===SITE_CONTEXT cache miss — loading from DB siteId={}", siteId);
        SiteSummary site = mapper.findSiteById(siteId);
        if (site == null) {
            log.warn("Site not found or inactive: {}", siteId);
            return null;
        }
        TemplateInfo template = null;
        if (site.getDefaultTemplateId() != null) {
            template = mapper.findTemplateById(site.getDefaultTemplateId());
            if (template == null) {
                log.warn("Default template missing for site={} (default_template_id={})",
                    siteId, site.getDefaultTemplateId());
            }
        }
        List<MenuNode> tree = buildMenuTree(mapper.findMenusBySiteId(siteId));
        log.info("===SITE_CONTEXT loaded siteId={} siteCode={} templateCode={} menuCount={}",
            siteId, site.getSiteCode(),
            template != null ? template.getTemplateCode() : "none",
            tree.size());
        return new SiteContext(site, template, tree);
    }

    /**
     * 도메인(호스트명) 으로 사이트 조회 후 동일 캐시 활용.
     * 매칭 없으면 DEFAULT 사이트로 폴백.
     */
    public SiteContext getContextByDomain(String host) {
        SiteSummary site = (host != null) ? mapper.findSiteByDomain(host) : null;
        if (site == null) {
            log.info("===SITE_CONTEXT domain not matched host={} — falling back to DEFAULT site", host);
            site = mapper.findDefaultSite();
        } else {
            log.info("===SITE_CONTEXT domain matched host={} siteCode={}", host, site.getSiteCode());
        }
        if (site == null) return null;
        return getContext(site.getSiteId());
    }

    /**
     * {@code site_code} (URL path-prefix 로 전달되는 값) 으로 사이트 조회 후 캐시 활용.
     * 미존재 코드면 {@code null} — 호출 측이 404 등으로 처리.
     */
    public SiteContext getContextByCode(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) return null;
        SiteSummary site = mapper.findSiteByCode(siteCode.trim());
        if (site == null) {
            log.info("===SITE_CONTEXT siteCode not found in DB siteCode={}", siteCode.trim());
            return null;
        }
        log.info("===SITE_CONTEXT siteCode resolved siteCode={} siteId={}", siteCode.trim(), site.getSiteId());
        return getContext(site.getSiteId());
    }

    /**
     * {@code menu_id} 로 메뉴의 소속 사이트를 역조회한 뒤 컨텍스트 반환.
     *
     * <p>반환 컨텍스트는 다음 3 값이 모두 셋팅된 상태:
     * <ul>
     *   <li>{@link SiteContext#getSiteId()}   — tb_menu.site_id</li>
     *   <li>{@link SiteContext#getSiteCode()} — tb_site.site_code (cached SiteSummary 경유)</li>
     *   <li>{@link SiteContext#getMenuId()}   — 입력 menuId 그대로</li>
     * </ul>
     *
     * <p>menuId 만 알고 있는 호출 측(예: 로그·통계·게시판 등 menu_id FK 보유 도메인) 이
     * 사이트 컨텍스트와 현재 메뉴 정보를 한 번에 받기 위한 진입점.
     *
     * @return menu 또는 site 가 없으면 null
     */
    public SiteContext getContextByMenuId(String menuId) {
        if (menuId == null || menuId.isBlank()) return null;
        String trimmed = menuId.trim();
        SiteSummary siteSummary = mapper.findSiteIdByMenuId(trimmed);

        String siteId = (siteSummary != null) ? siteSummary.getSiteId() : null;

        if (siteId == null || siteId.isBlank()) {
            log.info("getContextByMenuId — menu not found: menuId={}", trimmed);
            return null;
        }

        if(siteSummary.getLayoutPath() != null && !siteSummary.getLayoutPath().isBlank()) {
            TemplateInfo template = mapper.findTemplateById(siteSummary.getDefaultTemplateId());
            return new SiteContext(siteSummary, null, List.of());
        }

        SiteContext base = getContext(siteId);
        if (base == null) return null;
        SiteContext ctx = base.withMenuId(trimmed);
        log.info("getContextByMenuId — siteId={}, siteCode={}, menuId={}",
            ctx.getSiteId(), ctx.getSiteCode(), ctx.getMenuId());
        return ctx;
    }

    /**
     * menuId / siteId 우선순위 해석.
     *
     * <ol>
     *   <li>menuId 가 있으면 → menu 의 site_id 로 컨텍스트 + currentMenuId 동시 셋팅</li>
     *   <li>menuId 가 없고 siteId 가 있으면 → 해당 사이트 컨텍스트만 셋팅</li>
     *   <li>둘 다 비어있거나 해석 실패 → null</li>
     * </ol>
     */
    public SiteContext resolveContext(String siteId, String menuId) {
        if (menuId != null && !menuId.isBlank()) {
            SiteContext byMenu = getContextByMenuId(menuId);
            if (byMenu != null) return byMenu;
        }
        if (siteId != null && !siteId.isBlank()) {
            return getContext(siteId);
        }
        return null;
    }

    /**
     * 템플릿 코드로 단건 조회 (use_yn='Y', delete_yn='N') — 템플릿 프리뷰(?tmpl=CODE) 검증용.
     * 프리뷰 전환 시점에만 호출되고 결과는 세션에 보관되므로 캐시하지 않는다.
     *
     * @return 미존재/비활성 코드면 null
     */
    public TemplateInfo findTemplateByCode(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) return null;
        return mapper.findTemplateByCode(templateCode.trim());
    }

    // ------------------------------------------------------------------
    // EMPTY 폴백
    // ------------------------------------------------------------------

    /** 폴백 site_code — DB 에 미존재 시에도 하드코딩 최소 컨텍스트 제공. */
    public  static final String EMPTY_SITE_CODE     = "empty";
    /** 폴백 template_code — 대문자 규약. */
    public  static final String EMPTY_TEMPLATE_CODE = "EMPTY";
    private static final String EMPTY_LAYOUT_PATH   = "front/layouts/EMPTY/empty";

    /**
     * 1~4 계층 해석 모두 실패한 경우의 최종 폴백 {@link SiteContext}.
     *
     * <p>우선 DB 에서 {@code site_code='empty'} 를 조회하고,
     * seed 가 없으면 하드코딩된 최소 컨텍스트(빈 사이트명 · EMPTY 레이아웃) 를 in-memory 로 조립.
     * 이렇게 하면 DB 초기화 직후·테이블 비어있는 환경에서도 회원 로그인/가입 같은
     * 전역 페이지가 최소한의 레이아웃으로 렌더 가능.
     */
    public SiteContext getDefaultContext() {
        SiteContext ctx = getContextByCode(EMPTY_SITE_CODE);
        if (ctx != null) return ctx;
        log.info("===SITE_CONTEXT 'empty' site not found in DB — using hardcoded fallback");
        return hardcodedEmptyContext();
    }

    /**
     * DB seed 가 없을 때의 최종 in-memory fallback — "빈 사이트" 개념의 최소 컨텍스트.
     * 캐시하지 않음(매 요청마다 새 객체 — 비용 미미).
     */
    private static SiteContext hardcodedEmptyContext() {
        SiteSummary site = new SiteSummary();
        site.setSiteId("00000000-0000-0000-0000-000000000000");
        site.setSiteCode(EMPTY_SITE_CODE);
        site.setSiteName("");
        site.setDomain(null);
        site.setDefaultLang("ko");
        site.setDefaultTemplateId(null);
        site.setHeadMeta(null);
        site.setCopyright(null);
        site.setUseYn("Y");

        TemplateInfo tpl = new TemplateInfo();
        tpl.setTemplateId("00000000-0000-0000-0000-000000000000");
        tpl.setTemplateCode(EMPTY_TEMPLATE_CODE);
        tpl.setTemplateName("EMPTY (fallback)");
        tpl.setLayoutPath(EMPTY_LAYOUT_PATH);
        tpl.setUseYn("Y");

        return new SiteContext(site, tpl, List.of());
    }

    /**
     * 사이트/템플릿/메뉴 변경 시 해당 사이트 캐시 무효화.
     */
    @CacheEvict(value = CacheConfig.SITE_CONTEXT, key = "#siteId")
    public void evict(String siteId) {
        log.info("===SiteContext cache evicted: {}", siteId);
    }

    /**
     * 전체 사이트 캐시 무효화 (템플릿/메뉴 일괄 변경 시).
     */
    @CacheEvict(value = CacheConfig.SITE_CONTEXT, allEntries = true)
    public void evictAll() {
        log.info("===SiteContext cache evicted: ALL");
    }

    // ------------------------------------------------------------------------
    // 내부 헬퍼 — flat list → tree 조립
    // ------------------------------------------------------------------------
    private List<MenuNode> buildMenuTree(List<MenuNode> flat) {
        if (flat == null || flat.isEmpty()) return List.of();

        Map<String, MenuNode> byId = new HashMap<>(flat.size() * 2);
        for (MenuNode n : flat) byId.put(n.getMenuId(), n);

        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode n : flat) {
            String pid = n.getParentMenuId();
            if (pid == null) {
                roots.add(n);
            } else {
                MenuNode parent = byId.get(pid);
                if (parent != null) {
                    parent.getChildren().add(n);
                } else {
                    // 고아 노드 — 상위 메뉴가 비활성/삭제됐거나 데이터 불일치.
                    // 로깅 후 루트로 승격(사용자 접근성 유지).
                    log.info("Orphan menu (parent missing): menuId={}, parentMenuId={}",
                        n.getMenuId(), pid);
                    roots.add(n);
                }
            }
        }
        return roots;
    }
}
