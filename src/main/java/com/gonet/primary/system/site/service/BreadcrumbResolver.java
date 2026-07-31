package com.gonet.primary.system.site.service;

import com.gonet.primary.system.site.dto.MenuNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 메뉴 트리 + 현재 URI 를 입력받아 breadcrumb 경로(루트→현재) 를 산출.
 *
 * <p>매칭 규칙 — menuType 별로 canonical URL 을 합성:
 * <ul>
 *   <li>CONTENT — {@code /{siteCode}/{linkSlug}}</li>
 *   <li>BOARD   — {@code /bbs/{siteCode}/{linkBbsCode}}</li>
 *   <li>URL     — {@code linkUrl} (예: {@code /survey}, {@code /holiday})</li>
 *   <li>FOLDER  — 매칭 대상 아님 (조상 노드로만 표시)</li>
 * </ul>
 *
 * <p>현재 요청 URI 가 어떤 메뉴 노드의 canonical URL 과 일치하면, 그 노드부터
 * 부모 체인을 따라 루트까지 거슬러 올라가 trail 로 반환.
 *
 * <p>BOARD 의 detail 페이지 (예: {@code /bbs/airbnb/free/{articleId}}) 는 leaf URL 자체로
 * 매칭되지 않으므로 prefix 매칭으로 폴백 — leaf 메뉴(BOARD) 의 canonical URL 이 prefix 면
 * 그 메뉴를 마지막 node 로 사용.
 */
@Component
@RequiredArgsConstructor
public class BreadcrumbResolver {

    /**
     * menuId 를 명시적으로 받아 leaf → 루트 부모 체인 조립 후 root→leaf 순으로 반환.
     *
     * <p>menuId 가 null/blank 이거나 트리에서 찾지 못하면 빈 리스트 — fragment 가 미렌더.
     */
    public List<Crumb> resolveByMenuId(List<MenuNode> menuTree, String siteCode, String menuId) {
        if (menuTree == null || menuTree.isEmpty()
            || menuId == null || menuId.isBlank()) {
            return List.of();
        }
        Map<String, MenuNode> byId = new HashMap<>();
        flatten(menuTree, byId);

        MenuNode leaf = byId.get(menuId);
        if (leaf == null) return List.of();

        List<Crumb> chain = new ArrayList<>();
        MenuNode cur = leaf;
        while (cur != null) {
            chain.add(new Crumb(cur.getMenuName(), canonicalUrl(cur, siteCode)));
            cur = cur.getParentMenuId() == null ? null : byId.get(cur.getParentMenuId());
        }
        Collections.reverse(chain);
        return chain;
    }

    /**
     * 현재 요청 URI 로 메뉴를 역매칭해 trail 조립 — menuId 미주입 페이지(예: tb_bbs_master.menu_id
     * NULL 인 게시판, /prg 프로그램) 의 폴백.
     *
     * <p>매칭 우선순위: ① 정확 일치(동일 URL 이 여럿이면 <b>최심(자식) 노드</b> 우선 — 예:
     * 그룹 '교육' 과 하위 '교과과정' 이 같은 링크일 때 교과과정 선택) ② prefix 일치(가장 긴 것 —
     * 게시판 상세 {@code /bbs/me/curriculum/{id}} 등). URL 은 canonical 합성값 우선, 없으면
     * raw linkUrl (시드가 link_url 직접 저장하는 케이스).
     */
    public List<Crumb> resolveByUrl(List<MenuNode> menuTree, String siteCode, String uri) {
        if (menuTree == null || menuTree.isEmpty() || uri == null || uri.isBlank()) {
            return List.of();
        }
        int q = uri.indexOf('?');
        if (q >= 0) uri = uri.substring(0, q);
        while (uri.endsWith("/") && uri.length() > 1) uri = uri.substring(0, uri.length() - 1);

        Map<String, MenuNode> byId = new HashMap<>();
        flatten(menuTree, byId);

        MenuNode exact = null, prefix = null;
        int exactDepth = -1, prefixLen = -1;
        for (MenuNode n : byId.values()) {
            String cu = matchUrl(n, siteCode);
            if (cu == null) continue;
            if (uri.equals(cu)) {
                int d = depth(n, byId);
                if (d > exactDepth) { exact = n; exactDepth = d; }
            } else if (uri.startsWith(cu + "/") && cu.length() > prefixLen) {
                prefix = n; prefixLen = cu.length();
            }
        }
        MenuNode leaf = (exact != null) ? exact : prefix;
        if (leaf == null) return List.of();
        return resolveByMenuId(menuTree, siteCode, leaf.getMenuId());
    }

    // ----------------------------------------------------------------

    /** canonical URL 우선, 없으면 raw linkUrl — URL 역매칭용. */
    private static String matchUrl(MenuNode n, String siteCode) {
        String cu = canonicalUrl(n, siteCode);
        if (cu != null) return cu;
        String raw = n.getLinkUrl();
        if (raw == null || raw.isBlank() || "#".equals(raw.trim())) return null;
        String r = raw.trim();
        while (r.endsWith("/") && r.length() > 1) r = r.substring(0, r.length() - 1);
        return r;
    }

    /** 루트=0 기준 깊이 — 동일 URL 다중 매칭 시 자식 우선 판정용. */
    private static int depth(MenuNode n, Map<String, MenuNode> byId) {
        int d = 0;
        MenuNode cur = n;
        while (cur != null && cur.getParentMenuId() != null) {
            cur = byId.get(cur.getParentMenuId());
            d++;
            if (d > 20) break; // 순환 방어
        }
        return d;
    }

    private static void flatten(List<MenuNode> nodes, Map<String, MenuNode> out) {
        if (nodes == null) return;
        for (MenuNode n : nodes) {
            if (n == null) continue;
            out.put(n.getMenuId(), n);
            flatten(n.getChildren(), out);
        }
    }

    private static String canonicalUrl(MenuNode n, String siteCode) {
        if (n == null) return null;
        String type = n.getMenuType();
        if (type == null) return null;
        return switch (type.toUpperCase()) {
            case "CONTENT" -> (siteCode != null && n.getLinkSlug() != null && !n.getLinkSlug().isBlank())
                ? "/" + siteCode + "/" + n.getLinkSlug()
                : null;
            case "BOARD" -> (siteCode != null && n.getLinkBbsCode() != null && !n.getLinkBbsCode().isBlank())
                ? "/bbs/" + siteCode + "/" + n.getLinkBbsCode()
                : null;
            case "URL" -> (n.getLinkUrl() != null && !n.getLinkUrl().isBlank())
                ? n.getLinkUrl()
                : null;
            default -> null; // FOLDER 등은 매칭 대상 아님
        };
    }

    // ----------------------------------------------------------------

    /** Thymeleaf 출력용 — name + url. url 이 null 이면 비링크 텍스트. */
    @Getter
    public static class Crumb {
        private final String name;
        private final String url;
        public Crumb(String name, String url) {
            this.name = name;
            this.url  = url;
        }
    }
}
