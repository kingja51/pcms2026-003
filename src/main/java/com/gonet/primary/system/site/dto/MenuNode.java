package com.gonet.primary.system.site.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 메뉴 트리 노드 — tb_menu 1건 + 자식 메뉴 목록.
 *
 * <p>{@code SiteContextService} 는 1-query flat SELECT 로 가져온 뒤
 * in-memory 에서 parent_menu_id 로 트리를 조립한다.
 *
 * <p>{@code menuType} :
 * <ul>
 *   <li>CONTENT — tb_content 로 연결 (linkTargetId = content_id)</li>
 *   <li>BOARD   — tb_bbs_master 로 연결 (linkTargetId = bbs_master_id)</li>
 *   <li>URL     — 외부/직접 링크 (linkUrl 사용)</li>
 *   <li>FOLDER  — 하위 메뉴 컨테이너 (링크 없음)</li>
 * </ul>
 */
@Getter
@Setter
public class MenuNode {

    private String menuId;
    private String parentMenuId;
    private String menuName;
    private String menuType;
    private String linkTargetId;
    private String linkUrl;
    /** CONTENT 타입일 때 tb_content.slug (SiteContextMapper 가 LEFT JOIN 으로 채움). 다른 타입이면 null. */
    private String linkSlug;
    /** BOARD 타입일 때 tb_bbs_master.bbs_code (SiteContextMapper 가 LEFT JOIN 으로 채움). 다른 타입이면 null. */
    private String linkBbsCode;
    private int    sortOrder;
    private int    depth;
    private String authRequiredYn;

    private final List<MenuNode> children = new ArrayList<>();

    public boolean isRoot()        { return parentMenuId == null; }
    public boolean requiresAuth()  { return "Y".equalsIgnoreCase(authRequiredYn); }
    public boolean isLeaf()        { return children.isEmpty(); }
}
