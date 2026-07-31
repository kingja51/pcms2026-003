package com.gonet.primary.system.site.controller;

import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.dto.BbsArticleSearch;
import com.gonet.primary.board.article.dto.BbsArticleStatus;
import com.gonet.primary.board.article.service.BoardArticleService;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.dto.BbsMasterSearch;
import com.gonet.primary.board.master.service.BoardMasterService;
import com.gonet.primary.system.menu.dto.Menu;
import com.gonet.primary.system.menu.dto.MenuTreeNode;
import com.gonet.primary.system.menu.service.MenuService;
import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextResolver;
import com.gonet.primary.system.site.service.SiteContextService;
import com.gonet.primary.system.site.service.SiteTemplateScaffolder;
import com.gonet.primary.system.site.service.TemplatePreview;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 공통 사이트 쉘 — 모든 사이트의 {@code /{siteCode}/**} 를 단일 컨트롤러로 처리한다.
 *
 * <p>사이트를 추가할 때 컨트롤러를 복사하지 않는다. {@code tb_site} 행 + 메뉴·게시판·
 * 접근규칙 시드 + (선택) {@code templates/site/{siteCode}/} 콘텐츠 파일이면 동작한다.
 * 48개 학과 사이트 양산이 전제다.
 *
 * <p>라우트: {@code /{sc}}(→home) · {@code /{sc}/home} · {@code /{sc}/sitemap} ·
 * {@code /{sc}/{slug}}. 미등록 site_code 는 404. 미등록 URL 은 DB-RBAC 무매칭 DENY 가
 * 선차단하므로 {@code tb_role_url_access} 에 {@code /{sc}}·{@code /{sc}/**} 규칙이 있는
 * 사이트만 도달한다. {@code /bbs}·{@code /prg}·{@code /admin} 등 리터럴 매핑은 패턴보다 우선.
 *
 * <p>뷰 해석 순서(사이트 커스텀 → 공용 placeholder):
 * <ol>
 *   <li>{@code templates/site/{siteCode}/{slug}.html}(classpath 또는 외부 콘텐츠 루트
 *       {@code gopcms.content.sync.html-root}) 존재 시 그 뷰</li>
 *   <li>없으면 공용 뷰 {@code front/site/generic-*} — home/sitemap 은 항상, 그 외 slug 는
 *       {@code tb_menu} 에 {@code /{sc}/{slug}} 링크가 선언된 경우만. <b>"URL이 진실"</b> —
 *       메뉴에 없는 slug 는 404</li>
 * </ol>
 *
 * <p>레이아웃은 {@code tb_site.default_template_id} → {@code layout_path} 동적 decorate.
 * 배너·팝업·themeClass 는 {@link SiteContextModelAdvice} 가 전역 주입한다.
 *
 * <h2>001 대비 변경 — 일정·날씨 주입 제외</h2>
 * 001 은 홈에 {@code scheduleMasters}·{@code upcomingSchedules}(일정)와 {@code /{sc}/weather}
 * (기상 통계)를 주입했다. 003 은 <b>두 도메인 모두 아직 이식 대상이 아니다</b> —
 * 일정은 P6(부가 도메인), 날씨는 이식 계획 자체가 없다. 없는 서비스에 의존하면
 * 컨텍스트가 뜨지 않으므로 해당 주입과 {@code /weather} 라우트를 제외했다.
 *
 * <p>사이트 홈 템플릿 48종은 이 두 속성을 참조하지만 전부
 * {@code th:if="${upcomingSchedules != null and !#lists.isEmpty(...)}"} 로 감싸져 있어
 * <b>미주입 시 해당 섹션만 렌더되지 않는다</b>(예외 없음). P6 에서 일정 도메인을
 * 이식할 때 {@code injectLandingData} 에 주입을 되살리면 템플릿 수정 없이 살아난다.
 *
 * <p>eGov 호환성 규칙 3: Controller 는 주입된 Service 만 호출한다. Mapper 직접 호출 없음.
 */
@Controller
public class DefaultUsrController {

    private static final Logger log = LoggerFactory.getLogger(DefaultUsrController.class);

    /** site 에 default_template_id 가 없거나 조회 실패 시 최종 fallback. */
    private static final String FALLBACK_LAYOUT = "front/layouts/EMPTY/empty";

    /** URL 매핑 정규식 — site_code varchar(30), 안전 문자만(리다이렉트·뷰명 주입 차단). */
    private static final String SC_PATTERN   = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,29}";
    /** slug 화이트리스트 — path-traversal 방지. */
    private static final String SLUG_PATTERN = "[a-z0-9][a-z0-9_-]{0,63}";

    /** 좋아요·신고 조각을 노출하지 않을 slug — 랜딩·사이트맵 등 메타 페이지. */
    private static final Set<String> LIKE_REPORT_SUPPRESS_SLUGS = Set.of("home", "sitemap");

    /** 홈에 노출할 게시판별 최근 글 수. */
    private static final int LANDING_ARTICLES_LIMIT = 10;
    /** 홈에 노출할 게시판 수 상한. */
    private static final int LANDING_BOARDS_LIMIT = 50;

    private final MenuService            menuService;
    private final SiteContextService     siteContextService;
    private final BoardMasterService     boardMasterService;
    private final BoardArticleService    boardArticleService;
    private final SiteTemplateScaffolder scaffolder;

    /** {@code ContentFileWriter}·{@code ContentTemplateResolverConfig} 와 동일 경로. 미설정 시 classpath 만 확인. */
    @Value("${gopcms.content.sync.html-root:}")
    private String externalTemplateRoot;

    public DefaultUsrController(MenuService menuService,
                                SiteContextService siteContextService,
                                BoardMasterService boardMasterService,
                                BoardArticleService boardArticleService,
                                SiteTemplateScaffolder scaffolder) {
        this.menuService         = menuService;
        this.siteContextService  = siteContextService;
        this.boardMasterService  = boardMasterService;
        this.boardArticleService = boardArticleService;
        this.scaffolder          = scaffolder;
    }

    // ── /{sc} → /{sc}/home ──────────────────────────────────────────────

    @GetMapping("/{siteCode:" + SC_PATTERN + "}")
    public String root(@PathVariable String siteCode, HttpServletRequest req, Model model) {
        resolveOr404(siteCode, req, model);
        return "redirect:/" + siteCode + "/home";   // siteCode 는 매핑 정규식으로 안전 문자만
    }

    // ── /{sc}/home — 랜딩(게시판별 최근 글). 배너·메뉴는 공통 주입 ────────

    @GetMapping("/{siteCode:" + SC_PATTERN + "}/home")
    public String home(@PathVariable String siteCode, HttpServletRequest req, Model model) {
        SiteContext ctx = resolveOr404(siteCode, req, model);
        injectLandingData(ctx, model);
        if (siteTemplateExists(siteCode, "home")) return "site/" + siteCode + "/home";
        return "front/site/generic-home";
    }

    // ── /{sc}/sitemap (리터럴 세그먼트라 /{slug} 보다 우선) ───────────────

    @GetMapping("/{siteCode:" + SC_PATTERN + "}/sitemap")
    public String sitemap(@PathVariable String siteCode, HttpServletRequest req, Model model) {
        SiteContext ctx = resolveOr404(siteCode, req, model);
        List<MenuTreeNode> tree = menuService.sitemapTree(ctx.getSiteId());
        model.addAttribute("site",       ctx.getSite());
        model.addAttribute("menuTree",   tree);
        model.addAttribute("totalCount", countFlatten(tree));

        Menu sitemapMenu = menuService.findByLinkUrl(siteCode, "/" + siteCode + "/sitemap");
        if (sitemapMenu != null) model.addAttribute("menuId", sitemapMenu.getMenuId());

        if (siteTemplateExists(siteCode, "sitemap")) return "site/" + siteCode + "/sitemap";
        return "front/site/generic-sitemap";
    }

    // ── /{sc}/{slug} — 콘텐츠 페이지(커스텀 템플릿 또는 메뉴 선언 시 placeholder) ──

    @GetMapping("/{siteCode:" + SC_PATTERN + "}/{slug:" + SLUG_PATTERN + "}")
    public String page(@PathVariable String siteCode, @PathVariable String slug,
                       HttpServletRequest req, Model model) {
        resolveOr404(siteCode, req, model);
        injectPageMeta(siteCode, slug, model);

        if (siteTemplateExists(siteCode, slug)) return "site/" + siteCode + "/" + slug;

        // "URL이 진실" — 커스텀 파일도 없고 메뉴 선언도 없으면 그 URL 은 존재하지 않는다
        Menu menu = menuService.findByLinkUrl(siteCode, "/" + siteCode + "/" + slug);
        if (menu == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("menuId",    menu.getMenuId());   // breadcrumb 밴드 활성화
        model.addAttribute("pageTitle", menu.getMenuName());
        return "front/site/generic-page";
    }

    // ------------------------------------------------------------------

    /**
     * {@link SiteContext} 해석 + 공통 모델(siteContext·layoutPath·siteCode·theme) 주입 +
     * 세션 sticky + {@code ?tmpl=} 프리뷰 적용. 미등록 site_code 는 404.
     *
     * <p>세션 sticky 를 두는 이유: 이후 {@code /member/login} 같은 <b>평면 URL</b> 에는
     * siteCode 가 없어 사이트 레이아웃·테마를 잃는다. 직전 사이트를 세션에 남겨
     * {@link SiteContextResolver} 가 폴백으로 쓴다. <b>URL 이 진실이고 세션은 편의</b>다 —
     * URL 에 siteCode 가 있으면 항상 URL 이 이긴다.
     */
    private SiteContext resolveOr404(String siteCode, HttpServletRequest req, Model model) {
        SiteContext ctx = null;
        try {
            ctx = siteContextService.getContextByCode(siteCode);
        } catch (Exception ex) {
            log.warn("SITE_SHELL_RESOLVE_FAIL siteCode={} reason={}", siteCode, ex.getMessage());
        }
        if (ctx == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        // 템플릿 프리뷰(?tmpl=CODE / ?tmpl=off — 세션 sticky) — 데모·검수용 임시 레이아웃 교체
        ctx = TemplatePreview.apply(ctx, req, siteContextService);

        String layoutPath = (ctx.getLayoutPath() != null && !ctx.getLayoutPath().isBlank())
                ? ctx.getLayoutPath().trim() : FALLBACK_LAYOUT;

        model.addAttribute("siteContext", ctx);
        model.addAttribute("layoutPath",  layoutPath);
        model.addAttribute("siteCode",    siteCode);
        model.addAttribute("theme",       ctx.getTheme());
        model.addAttribute("menuTree",    ctx.getMenuTree());

        HttpSession sess = req.getSession(true);
        Object cur = sess.getAttribute(SiteContextResolver.SESSION_SITE_CODE);
        if (!siteCode.equals(cur)) sess.setAttribute(SiteContextResolver.SESSION_SITE_CODE, siteCode);

        // 콘텐츠 폴더 없는 사이트에 샘플 서식 복사 — 파일 단위 멱등, 실패해도 무해
        scaffolder.ensureScaffold(siteCode);
        return ctx;
    }

    /**
     * 랜딩(home) 데이터 — 사이트의 활성 게시판 전체({@code siteBoards})와 게시판별 최근 글
     * ({@code articlesByBbs[bbsCode]}). {@code noticeArticles} 는 템플릿 하위호환 별칭이다.
     *
     * <p>각 조회는 실패 시 빈 컬렉션으로 떨어진다 — <b>홈이 5xx 로 죽지 않는 것</b>이
     * 개별 섹션이 비는 것보다 중요하다.
     *
     * <p>001 의 {@code scheduleMasters}·{@code upcomingSchedules} 는 일정 도메인(P6) 미이식이라
     * 주입하지 않는다. 클래스 javadoc 참조.
     */
    private void injectLandingData(SiteContext ctx, Model model) {
        List<BbsMaster> boards = activeBoards(ctx.getSiteId());
        Map<String, List<BbsArticle>> articlesByBbs = new LinkedHashMap<>();
        for (BbsMaster b : boards) articlesByBbs.put(b.getBbsCode(), recentArticles(b));

        model.addAttribute("siteBoards",     boards);
        model.addAttribute("articlesByBbs",  articlesByBbs);
        model.addAttribute("noticeArticles", articlesByBbs.getOrDefault("notice", List.of()));
    }

    /** 사이트 활성 게시판 목록(정의 순). 실패 시 빈 리스트. */
    private List<BbsMaster> activeBoards(String siteId) {
        try {
            BbsMasterSearch q = new BbsMasterSearch();
            q.setSiteId(siteId);
            q.setUseYn("Y");
            q.setPage(1);
            q.setPageSize(LANDING_BOARDS_LIMIT);
            List<BbsMaster> boards = boardMasterService.search(q);
            return boards != null ? boards : List.of();
        } catch (Exception ex) {
            log.warn("SITE_BOARDS_FAIL site={} reason={}", siteId, ex.getMessage());
            return List.of();
        }
    }

    /** 게시판 최근 PUBLISHED 글 N건. 조회 실패 시 빈 리스트. */
    private List<BbsArticle> recentArticles(BbsMaster master) {
        try {
            BbsArticleSearch q = new BbsArticleSearch();
            q.setBbsMasterId(master.getBbsMasterId());
            q.setStatus(BbsArticleStatus.PUBLISHED.name());
            q.setIncludeNotice(true);
            q.setPage(1);
            q.setPageSize(LANDING_ARTICLES_LIMIT);
            List<BbsArticle> articles = boardArticleService.search(q);
            return articles != null ? articles : Collections.emptyList();
        } catch (Exception ex) {
            log.warn("SITE_HOME_RECENT_FAIL bbs={} reason={}", master.getBbsCode(), ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 콘텐츠 페이지 메타 — 좋아요·신고 조각용 가상 {@code contentId}({@code "{sc}:{slug}"}) +
     * breadcrumb 용 {@code menuId}.
     *
     * <p>URI 전체를 contentId 로 쓰지 않는다 — 슬래시가 path variable 에 들어가면
     * {@code StrictHttpFirewall} 이 거부한다. 실제 {@code tb_content} 페이지에서는
     * 컨트롤러가 진짜 contentId 로 덮어쓴다.
     */
    private void injectPageMeta(String siteCode, String slug, Model model) {
        if (slug == null || slug.isBlank() || LIKE_REPORT_SUPPRESS_SLUGS.contains(slug)) return;
        String composite = siteCode + ":" + slug;
        if (composite.length() <= 36) model.addAttribute("contentId", composite);
        Menu menu = menuService.findBySiteCode(siteCode, slug);
        if (menu != null) model.addAttribute("menuId", menu.getMenuId());
    }

    /**
     * {@code templates/site/{siteCode}/{slug}.html} 존재 여부 — classpath 또는 외부 콘텐츠 루트
     * ({@code gopcms.content.sync.html-root}/site/).
     */
    private boolean siteTemplateExists(String siteCode, String slug) {
        if (new ClassPathResource("templates/site/" + siteCode + "/" + slug + ".html").exists()) return true;
        if (externalTemplateRoot == null || externalTemplateRoot.isBlank()) return false;
        Path p = Paths.get(externalTemplateRoot, "site", siteCode, slug + ".html");
        return Files.isRegularFile(p);
    }

    private int countFlatten(List<MenuTreeNode> nodes) {
        int n = 0;
        for (MenuTreeNode node : nodes) { n++; n += countFlatten(node.getChildren()); }
        return n;
    }
}
