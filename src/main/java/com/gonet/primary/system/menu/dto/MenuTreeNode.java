package com.gonet.primary.system.menu.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 화면용 메뉴 트리 노드 — {@link Menu} 확장 (children 포함).
 *
 * <p>Service 가 flat 조회 결과를 in-memory 로 조립해 루트 목록으로 반환.
 * <p>list.html 에서 재귀 fragment 로 렌더링.
 */
@Getter
@RequiredArgsConstructor
public class MenuTreeNode {

    private final Menu self;
    private final List<MenuTreeNode> children = new ArrayList<>();

    // Thymeleaf 편의 getter (self 위임)
    public String getMenuId()        { return self.getMenuId(); }
    public String getSiteId()        { return self.getSiteId(); }
    public String getMenuName()      { return self.getMenuName(); }
    public String getMenuType()      { return self.getMenuType(); }
    public int    getSortOrder()     { return self.getSortOrder(); }
    public int    getDepth()         { return self.getDepth(); }
    public String getUseYn()         { return self.getUseYn(); }
    public String getLinkUrl()       { return self.getLinkUrl(); }
    public String getLinkTargetId()  { return self.getLinkTargetId(); }
    public String getLinkSlug()      { return self.getLinkSlug(); }
    public String getParentMenuId()  { return self.getParentMenuId(); }
    public boolean isLeaf()          { return children.isEmpty(); }
    public boolean isFolder()        { return self.isFolder(); }
    public boolean isUrl()           { return self.isUrl(); }
    public boolean isContent()       { return self.isContent(); }
    public boolean isBoard()         { return self.isBoard(); }
}
