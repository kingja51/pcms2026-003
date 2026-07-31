package com.gonet.primary.system.menu.service;

import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.system.menu.dto.Menu;
import com.gonet.primary.system.menu.dto.MenuSaveForm;
import com.gonet.primary.system.menu.dto.MenuSearch;
import com.gonet.primary.system.menu.dto.MenuTreeNode;
import com.gonet.primary.system.menu.mapper.MenuMapper;
import com.gonet.primary.system.site.dto.SiteContextChangedEvent;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 메뉴 관리 서비스 구현 (eGov 표준).
 *
 * <p>주요 로직:
 * <ul>
 *   <li>트리 조립 — flat list → parent_menu_id 로 children 연결</li>
 *   <li>depth 자동 계산 — 부모.depth + 1, root 는 1</li>
 *   <li>sort_order 자동 부여 — 형제 최대값 + 10 (간격으로 중간 삽입 여지)</li>
 *   <li>타입별 필드 정합성 검증 — CONTENT/BOARD 는 link_target_id, URL 은 link_url, FOLDER 는 둘 다 null</li>
 *   <li>up/down 이동 — 형제 목록에서 인접 swap</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MenuServiceImpl extends EgovAbstractServiceImpl implements MenuService {

    private static final int SORT_GAP = 10;

    private final MenuMapper mapper;
    private final ApplicationEventPublisher publisher;

    public MenuServiceImpl(MenuMapper mapper,
                            ApplicationEventPublisher publisher) {
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @Override
    public List<MenuTreeNode> tree(MenuSearch search) {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(search.getSiteId(), "siteId");
        List<Menu> flat = mapper.findBySite(search);
        return buildTree(flat);
    }

    @Override
    public List<Menu> searchFlat(MenuSearch search) {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(search.getSiteId(), "siteId");
        return mapper.findFlat(search);
    }

    @Override
    public List<MenuTreeNode> sitemapTree(String siteId) {
        if (siteId == null || siteId.isBlank()) return List.of();
        List<Menu> flat = mapper.findSitemapFlat(siteId);
        return buildTree(flat);
    }

    @Override
    public Menu get(String menuId) {
        if (menuId == null || menuId.isBlank()) return null;
        return mapper.findById(menuId);
    }

    @Override
    public Menu findBySiteCode(String siteCode, String slug) {
        if (siteCode == null || siteCode.isBlank()) return null;
        if (slug == null || slug.isBlank()) return null;
        return mapper.findBySiteCode(siteCode, slug);
    }

    @Override
    public Menu findByLinkUrl(String siteCode, String linkUrl) {
        if (siteCode == null || siteCode.isBlank()) return null;
        if (linkUrl == null || linkUrl.isBlank()) return null;
        return mapper.findByLinkUrl(siteCode, linkUrl);
    }

    @Override
    public List<Menu> findParentCandidates(String siteId, String excludeMenuId) {
        MenuSearch s = new MenuSearch();
        s.setSiteId(siteId);
        s.setUseYn("Y");
        List<Menu> all = mapper.findBySite(s);
        if (excludeMenuId == null || excludeMenuId.isBlank()) return all;

        // 자기 자신 + 모든 후손을 제외 (순환 참조 방지)
        Set<String> excluded = new HashSet<>();
        excluded.add(excludeMenuId);
        boolean changed;
        do {
            changed = false;
            for (Menu m : all) {
                if (!excluded.contains(m.getMenuId())
                    && m.getParentMenuId() != null
                    && excluded.contains(m.getParentMenuId())) {
                    excluded.add(m.getMenuId());
                    changed = true;
                }
            }
        } while (changed);

        return all.stream()
            .filter(m -> !excluded.contains(m.getMenuId()))
            .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(MenuSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getSiteId() == null || form.getSiteId().isBlank()) {
            throw new IllegalArgumentException("siteId required");
        }
        validateByType(form);

        int depth = calculateDepth(form.getParentMenuId());
        int sort = nextSortOrder(form.getSiteId(), form.getParentMenuId());

        Menu m = new Menu();
        m.setMenuId(UuidV7Generator.generate("MNU"));
        m.setSiteId(form.getSiteId());
        m.setParentMenuId(blankToNull(form.getParentMenuId()));
        m.setMenuName(form.getMenuName().trim());
        m.setMenuType(form.getMenuType().toUpperCase());
        m.setLinkTargetId(blankToNull(form.getLinkTargetId()));
        m.setLinkUrl(blankToNull(form.getLinkUrl()));
        m.setSortOrder(sort);
        m.setDepth(depth);
        m.setAuthRequiredYn(form.getAuthRequiredYn() == null ? "N" : form.getAuthRequiredYn());
        m.setUseYn(form.getUseYn() == null ? "Y" : form.getUseYn());

        mapper.insert(m);
        publisher.publishEvent(new SiteContextChangedEvent(
            m.getSiteId(), SiteContextChangedEvent.Reason.MENU_CHANGED));
        // 검색 색인 훅 없음 — 검색은 외부 엔진(contextPath=/search)이 담당한다(D10)
        return m.getMenuId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(MenuSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getMenuId() == null || form.getMenuId().isBlank()) {
            throw new IllegalArgumentException("menuId required");
        }
        Menu existing = mapper.findById(form.getMenuId());
        if (existing == null) {
            throw new IllegalArgumentException("메뉴를 찾을 수 없습니다: " + form.getMenuId());
        }
        if (form.getSiteId() != null && !existing.getSiteId().equals(form.getSiteId())) {
            throw new IllegalArgumentException("메뉴의 사이트는 변경할 수 없습니다.");
        }
        validateByType(form);

        existing.setMenuName(form.getMenuName().trim());
        existing.setMenuType(form.getMenuType().toUpperCase());
        existing.setLinkTargetId(blankToNull(form.getLinkTargetId()));
        existing.setLinkUrl(blankToNull(form.getLinkUrl()));
        existing.setAuthRequiredYn(form.getAuthRequiredYn() == null ? existing.getAuthRequiredYn() : form.getAuthRequiredYn());
        existing.setUseYn(form.getUseYn() == null ? existing.getUseYn() : form.getUseYn());

        mapper.update(existing);
        publisher.publishEvent(new SiteContextChangedEvent(
            existing.getSiteId(), SiteContextChangedEvent.Reason.MENU_CHANGED));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void move(String menuId, String direction) {
        if (menuId == null || menuId.isBlank()) {
            throw new IllegalArgumentException("menuId required");
        }
        if (!"up".equalsIgnoreCase(direction) && !"down".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("direction must be 'up' or 'down'");
        }
        Menu target = mapper.findById(menuId);
        if (target == null) {
            throw new IllegalArgumentException("메뉴를 찾을 수 없습니다: " + menuId);
        }
        List<Menu> siblings = mapper.findSiblings(target.getSiteId(), target.getParentMenuId());
        int idx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (menuId.equals(siblings.get(i).getMenuId())) { idx = i; break; }
        }
        if (idx < 0) return;

        int swapIdx = "up".equalsIgnoreCase(direction) ? idx - 1 : idx + 1;
        if (swapIdx < 0 || swapIdx >= siblings.size()) return; // 경계

        Menu other = siblings.get(swapIdx);
        int targetSort = target.getSortOrder();
        int otherSort  = other.getSortOrder();

        // 동일값이면 간격 재부여
        if (targetSort == otherSort) {
            mapper.updateSortOrder(target.getMenuId(), "up".equalsIgnoreCase(direction) ? otherSort - 1 : otherSort + 1);
        } else {
            mapper.updateSortOrder(target.getMenuId(), otherSort);
            mapper.updateSortOrder(other.getMenuId(), targetSort);
        }

        publisher.publishEvent(new SiteContextChangedEvent(
            target.getSiteId(), SiteContextChangedEvent.Reason.MENU_CHANGED));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String menuId) {
        if (menuId == null || menuId.isBlank()) {
            throw new IllegalArgumentException("menuId required");
        }
        Menu existing = mapper.findById(menuId);
        if (existing == null) {
            throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 메뉴입니다.");
        }
        int children = mapper.countChildren(menuId);
        if (children > 0) {
            throw new IllegalArgumentException(
                "하위 메뉴가 " + children + " 개 있어 삭제할 수 없습니다. 먼저 하위 메뉴를 정리하세요.");
        }
        int contentRefs = mapper.countContentReferences(menuId);
        if (contentRefs > 0) {
            throw new IllegalArgumentException(
                "이 메뉴를 참조하는 콘텐츠가 " + contentRefs + " 개 있어 삭제할 수 없습니다. "
                + "콘텐츠의 메뉴 연결을 먼저 해제하세요.");
        }
        int affected = mapper.softDelete(menuId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + menuId);
        }
        publisher.publishEvent(new SiteContextChangedEvent(
            existing.getSiteId(), SiteContextChangedEvent.Reason.MENU_CHANGED));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String seedRootMenuIfAbsent(String siteId, String menuName) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("siteId required");
        }
        MenuSearch probe = new MenuSearch();
        probe.setSiteId(siteId);
        List<Menu> existing = mapper.findBySite(probe);
        if (existing != null && !existing.isEmpty()) {
            return null;
        }
        String name = (menuName == null || menuName.isBlank()) ? "홈" : menuName.trim();

        Menu root = new Menu();
        root.setMenuId(UuidV7Generator.generate("MNU"));
        root.setSiteId(siteId);
        root.setParentMenuId(null);
        root.setMenuName(name);
        root.setMenuType(Menu.TYPE_FOLDER);
        root.setLinkTargetId(null);
        root.setLinkUrl(null);
        root.setSortOrder(SORT_GAP);
        root.setDepth(1);
        root.setAuthRequiredYn("N");
        root.setUseYn("Y");

        mapper.insert(root);
        return root.getMenuId();
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    private void validateByType(MenuSaveForm form) {
        String type = form.getMenuType() == null ? "" : form.getMenuType().toUpperCase();
        switch (type) {
            case Menu.TYPE_CONTENT, Menu.TYPE_BOARD -> {
                if (form.getLinkTargetId() == null || form.getLinkTargetId().isBlank()) {
                    throw new IllegalArgumentException(type + " 메뉴는 link_target_id 가 필요합니다.");
                }
            }
            case Menu.TYPE_URL -> {
                if (form.getLinkUrl() == null || form.getLinkUrl().isBlank()) {
                    throw new IllegalArgumentException("URL 메뉴는 link_url 이 필요합니다.");
                }
            }
            case Menu.TYPE_FOLDER -> {
                form.setLinkTargetId(null);
                form.setLinkUrl(null);
            }
            default -> throw new IllegalArgumentException("알 수 없는 menu_type: " + type);
        }
    }

    private int calculateDepth(String parentMenuId) {
        if (parentMenuId == null || parentMenuId.isBlank()) return 1;
        Menu parent = mapper.findById(parentMenuId);
        if (parent == null) {
            throw new IllegalArgumentException("부모 메뉴를 찾을 수 없습니다: " + parentMenuId);
        }
        return parent.getDepth() + 1;
    }

    private int nextSortOrder(String siteId, String parentMenuId) {
        Integer max = mapper.maxSortOrder(siteId, blankToNull(parentMenuId));
        return (max == null ? 0 : max) + SORT_GAP;
    }

    private List<MenuTreeNode> buildTree(List<Menu> flat) {
        if (flat == null || flat.isEmpty()) return List.of();

        Map<String, MenuTreeNode> byId = new HashMap<>(flat.size() * 2);
        for (Menu m : flat) byId.put(m.getMenuId(), new MenuTreeNode(m));

        List<MenuTreeNode> roots = new LinkedList<>();
        for (Menu m : flat) {
            MenuTreeNode node = byId.get(m.getMenuId());
            if (m.getParentMenuId() == null) {
                roots.add(node);
            } else {
                MenuTreeNode parent = byId.get(m.getParentMenuId());
                if (parent != null) parent.getChildren().add(node);
                else roots.add(node); // 고아 방어 — 루트로 승격
            }
        }
        return new ArrayList<>(roots);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
