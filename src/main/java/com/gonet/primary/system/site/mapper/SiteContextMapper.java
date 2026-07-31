package com.gonet.primary.system.site.mapper;

import com.gonet.primary.system.site.dto.MenuNode;
import com.gonet.primary.system.site.dto.SiteSummary;
import com.gonet.primary.system.site.dto.TemplateInfo;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SiteContext 로드용 Mapper — 캐시 미스 시에만 호출.
 *
 * <p>3개 쿼리로 분리 (JOIN 없이):
 * <ol>
 *   <li>{@link #findSiteById}     — tb_site (template_id 포함)</li>
 *   <li>{@link #findTemplateById} — tb_template (template_id 있을 때만)</li>
 *   <li>{@link #findMenusBySiteId} — tb_menu 전체 (flat, in-memory 트리 조립)</li>
 * </ol>
 *
 * <p>도메인 → site 역조회도 제공 ({@link #findSiteByDomain}).
 */
@EgovMapper
public interface SiteContextMapper {

    /** site_id 로 단건 조회 (delete_yn='N', use_yn='Y') */
    SiteSummary findSiteById(@Param("siteId") String siteId);

    /** 도메인(host) 로 단건 조회 — 멀티사이트 라우팅 엔트리포인트 */
    SiteSummary findSiteByDomain(@Param("domain") String domain);

    /** site_code 로 단건 조회 — 관리자/URL slug 경로용 */
    SiteSummary findSiteByCode(@Param("siteCode") String siteCode);

    /** 기본 사이트(DEFAULT) — 도메인 매칭 실패 시 폴백 */
    SiteSummary findDefaultSite();

    /** 템플릿 단건 (use_yn='Y', delete_yn='N') — null 가능 */
    TemplateInfo findTemplateById(@Param("templateId") String templateId);

    /** 템플릿 코드로 단건 (use_yn='Y', delete_yn='N') — 템플릿 프리뷰(?tmpl=) 검증용. null 가능 */
    TemplateInfo findTemplateByCode(@Param("templateCode") String templateCode);

    /** 사이트의 전체 메뉴 flat 조회 (sort_order ASC) — Service 가 parent_menu_id 로 트리 조립 */
    List<MenuNode> findMenusBySiteId(@Param("siteId") String siteId);

    /** menu_id 로 site_id 만 조회 — getContextByMenuId 진입점에서 사용 (delete_yn='N') */
    SiteSummary findSiteIdByMenuId(@Param("menuId") String menuId);
}
