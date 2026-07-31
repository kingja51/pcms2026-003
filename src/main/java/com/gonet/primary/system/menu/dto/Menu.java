package com.gonet.primary.system.menu.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_menu 1건 — CRUD 풀 엔티티.
 *
 * <p>경량 버전 {@code MenuNode} (SiteContext 로드용) 과 역할 분리:
 * <ul>
 *   <li>{@code MenuNode} : 런타임 메뉴 트리(사용자 프런트) 전용</li>
 *   <li>{@link Menu}     : 관리자 화면/엑셀/감사 필드 포함</li>
 * </ul>
 *
 * <p>menu_type:
 * <ul>
 *   <li>CONTENT — tb_content 연결 (link_target_id = content_id)</li>
 *   <li>BOARD   — tb_bbs_master 연결 (link_target_id = bbs_master_id)</li>
 *   <li>URL     — 직접 링크 (link_url)</li>
 *   <li>FOLDER  — 하위 메뉴 컨테이너 (링크 없음)</li>
 * </ul>
 */
@Getter
@Setter
public class Menu extends BaseEntity implements SoftDeletable, UseFlagged {

    public static final String TYPE_CONTENT = "CONTENT";
    public static final String TYPE_BOARD   = "BOARD";
    public static final String TYPE_URL     = "URL";
    public static final String TYPE_FOLDER  = "FOLDER";

    private String menuId;
    private String siteId;
    private String parentMenuId;
    private String menuName;
    private String menuType;
    private String linkTargetId;
    private String linkUrl;
    private int    sortOrder;
    private int    depth;
    private String authRequiredYn;
    private String useYn;
    private String deleteYn;

    private String parentMenuName;
    private String siteCode;

    /**
     * Sitemap 전용 transient — CONTENT 타입일 때 tb_content.slug 를 JOIN 으로 채움.
     * 일반 CRUD 쿼리에서는 NULL. {@link com.gonet.primary.system.menu.mapper.MenuMapper#findSitemapFlat} 전용.
     */
    private String linkSlug;

    // content
    private String body;
    private String originalContent;
    private String summary;

    public boolean isFolder()  { return TYPE_FOLDER.equalsIgnoreCase(menuType); }
    public boolean isUrl()     { return TYPE_URL.equalsIgnoreCase(menuType); }
    public boolean isContent() { return TYPE_CONTENT.equalsIgnoreCase(menuType); }
    public boolean isBoard()   { return TYPE_BOARD.equalsIgnoreCase(menuType); }
}
