package com.gonet.primary.system.menu.mapper;

import com.gonet.primary.system.menu.dto.Menu;
import com.gonet.primary.system.menu.dto.MenuSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_menu CRUD Mapper (관리자 전용).
 *
 * <p>{@code SiteContextMapper.findMenusBySiteId} 는 사용자 프런트 로드용,
 * 본 Mapper 는 관리자 CUD + soft-deleted 미노출 + 검색/정렬용.
 */
@EgovMapper
public interface MenuMapper {

    /** 사이트 내 트리 전체 flat 조회 — Service 가 트리 조립 */
    List<Menu> findBySite(@Param("search") MenuSearch search);

    /**
     * Sitemap 전용 — 사이트의 활성 메뉴 flat 목록 + {@code tb_content.slug} JOIN.
     * 결과의 {@code linkSlug} 필드가 채워져 Thymeleaf 에서 URL 빌드 가능.
     */
    List<Menu> findSitemapFlat(@Param("siteId") String siteId);

    /** 검색어 있는 flat 결과 (트리 조립 생략) */
    List<Menu> findFlat(@Param("search") MenuSearch search);

    Menu findById(@Param("menuId") String menuId);

    Menu findBySiteCode(@Param("siteCode") String siteCode, @Param("slug") String slug);

    Menu findByLinkUrl(@Param("siteCode") String siteCode, @Param("linkUrl") String linkUrl);


    /** 형제 메뉴 조회 — 정렬 이동 계산용 */
    List<Menu> findSiblings(@Param("siteId") String siteId,
                              @Param("parentMenuId") String parentMenuId);

    /** 자식 메뉴 수 (삭제 가능 여부 검증) */
    int countChildren(@Param("menuId") String menuId);

    /**
     * {@code tb_content.menu_id} 참조 수 — orphan 가드용.
     *
     * <p>2026-04-23 {@code fk_content_menu} FK DROP 이후 DB 계층 정합성 보호가 사라져
     * 애플리케이션 측 검증이 필요. 삭제되지 않은 콘텐츠만 카운트.
     */
    int countContentReferences(@Param("menuId") String menuId);

    /** 해당 site 의 최대 sortOrder (append 시) */
    Integer maxSortOrder(@Param("siteId") String siteId,
                          @Param("parentMenuId") String parentMenuId);

    int insert(Menu menu);

    int update(Menu menu);

    /** 정렬 순서만 변경 (up/down 이동) */
    int updateSortOrder(@Param("menuId") String menuId,
                         @Param("sortOrder") int sortOrder);

    int softDelete(@Param("menuId") String menuId);

    /**
     * 색인 재구축용 — 활성 메뉴 전체. use_yn 무관 (Service 가 use_yn='Y' + 링크 가능 타입만 색인).
     * tb_content.slug + tb_bbs_master.bbs_code 를 LEFT JOIN 으로 함께 노출해 URL 생성 자료로 사용.
     */
    List<Menu> findAllForReindex();
}
