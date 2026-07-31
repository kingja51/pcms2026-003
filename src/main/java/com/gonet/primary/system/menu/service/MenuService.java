package com.gonet.primary.system.menu.service;

import com.gonet.primary.system.menu.dto.Menu;
import com.gonet.primary.system.menu.dto.MenuSaveForm;
import com.gonet.primary.system.menu.dto.MenuSearch;
import com.gonet.primary.system.menu.dto.MenuTreeNode;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 메뉴 관리 서비스 (관리자용).
 *
 * <p>eGov 호환성: 인터페이스 + {@link MenuServiceImpl} (EgovAbstractServiceImpl 상속).
 * CUD 시 {@code SiteContextChangedEvent}(MENU_CHANGED) 발행.
 */
public interface MenuService {

    /** 사이트 전체 메뉴 트리 (루트 목록 반환) */
    List<MenuTreeNode> tree(MenuSearch search);

    /**
     * Sitemap 전용 트리 — 활성(use_yn=Y) 메뉴만 + {@code tb_content.slug} JOIN 포함.
     * 각 노드의 {@code self.linkSlug} 가 채워져 URL 생성에 그대로 사용 가능.
     */
    List<MenuTreeNode> sitemapTree(String siteId);

    /** 검색어 포함 flat 목록 */
    List<Menu> searchFlat(MenuSearch search);

    Menu get(String menuId);


    Menu findBySiteCode(String siteCode, String slug);

    Menu findByLinkUrl(String siteCode, String linkUrl);


    /** 부모 후보 드롭다운용 — 자기 자신 + 하위 제외 */
    List<Menu> findParentCandidates(String siteId, String excludeMenuId);

    String create(MenuSaveForm form);

    void update(MenuSaveForm form);

    /** up=이전 형제와 swap, down=다음 형제와 swap */
    void move(String menuId, String direction);

    /** soft delete — 자식 메뉴 있으면 예외 */
    void softDelete(String menuId);

    /**
     * 신규 사이트용 루트 메뉴 자동 seed — "홈" (TYPE=FOLDER, depth=0, sortOrder=1).
     *
     * <p>{@code SiteServiceImpl.create()} 에서 사이트 생성 직후 호출. 이미 메뉴가 있으면 no-op.
     * 동일 레이아웃을 쓰는 신규 사이트가 빈 내비로 렌더되지 않도록 보장 (I-012).
     *
     * <p>이벤트는 발행하지 않음 — 호출 측 {@code SiteServiceImpl.create} 가 이미
     * {@code SITE_UPDATED} 이벤트를 발행하므로 동일 siteId 캐시가 무효화됨.
     *
     * @return 생성된 루트 menuId 또는 이미 존재 시 null
     */
    String seedRootMenuIfAbsent(String siteId, String menuName);
}
