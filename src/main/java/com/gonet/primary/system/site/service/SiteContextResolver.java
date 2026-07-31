package com.gonet.primary.system.site.service;

import com.gonet.primary.system.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * 사이트 컨텍스트 해석 + Thymeleaf 레이아웃 호환 모델 주입 헬퍼.
 *
 * <p>SiteContextInterceptor 제거 이후 — 사이트별 레이아웃이 필요한 컨트롤러에서
 * 직접 호출해 model 에 {@code siteContext / siteCode / siteId / layoutPath} 를 주입한다.
 *
 * <p>해석 우선순위 (siteCode):
 * <ol>
 *   <li>request param {@code ?siteCode=...} — 폼 hidden 또는 query string (최우선)</li>
 *   <li>request attribute {@code siteCode} — forward 시 보존된 값</li>
 *   <li>model attribute {@code siteCode} — 다른 `@ModelAttribute` 가 먼저 채운 값</li>
 *   <li>session attribute {@link #SESSION_SITE_CODE} — 멀티페이지 sticky 폴백</li>
 * </ol>
 *
 * <p>1~3 단계로 해석 성공 시 session 에 {@code PCMS_SITE_CODE} 를 갱신해 다음 요청의 폴백으로 사용.
 * 4 단계(session)만으로 해석된 경우는 session 그대로 유지.
 *
 * <p>⚠️ 멀티탭 회귀(I-014) 가드 — 폼 제출 시 {@code fragments/site-context :: hiddenSiteCode} fragment 로
 * 명시 파라미터(1단계) 를 항상 전파해야 sticky(4단계) 가 다른 탭의 값으로 덮어쓰는 것을 방지.
 *
 * <p>사용 예 — `@ModelAttribute` 메서드 안에서:
 * <pre>
 *   &#64;ModelAttribute
 *   public void injectSiteContext(HttpServletRequest req, Model model) {
 *       siteContextResolver.inject(req, model);
 *   }
 * </pre>
 *
 * <p>master 가 있는 도메인(schedule/survey/board) 은 {@link #injectByCode(String, Model)} 을
 * 사용해 master 에서 추출한 siteCode 를 직접 전달.
 */
@Component
public class SiteContextResolver {

    private static final Logger log = LoggerFactory.getLogger(SiteContextResolver.class);

    /** 컨텍스트 해석 실패 시 사용할 EMPTY 레이아웃. */
    public static final String EMPTY_LAYOUT = "front/layouts/EMPTY/empty";

    /** session sticky 키 — 페이지 이동 간 siteCode 유지용. */
    public static final String SESSION_SITE_CODE = "PCMS_SITE_CODE";

    private final SiteContextService siteContextService;
    private final SiteContextHolder siteContextHolder;   // ← 추가 주입

    public SiteContextResolver(SiteContextService siteContextService,
                               SiteContextHolder siteContextHolder) {
        this.siteContextService = siteContextService;
        this.siteContextHolder  = siteContextHolder;
    }




    /**
     * request param/attr/model/session 우선순위로 siteCode 를 해석하고 model 에 주입.
     * 어느 단계에서도 siteCode 를 찾지 못하면 no-op (EMPTY 폴백).
     *
     * <p>request/model 에서 명시적으로 해석된 siteCode 는 session 에 sticky 로 저장 →
     * 같은 세션의 후속 요청(예: {@code /member/mypage}) 이 파라미터 없이도 동일 사이트로 렌더.
     */
    public void inject(HttpServletRequest req, Model model) {
        String siteCode = resolveExplicitSiteCode(req, model);  // session 폴백 제외
        if (siteCode != null) {
            log.info("===SITE_CONTEXT resolved from request siteCode={} uri={}",
                siteCode, req.getRequestURI());
            saveSticky(req, siteCode);
        } else {
            // 명시 파라미터 없으면 session 에서 폴백
            siteCode = readSticky(req);
            if (siteCode != null) {
                log.info("===SITE_CONTEXT resolved from session sticky siteCode={} uri={}",
                    siteCode, req.getRequestURI());
            }
        }
        injectByCode(siteCode, req, model);
    }

    /**
     * siteCode 가 path variable 등으로 명시적으로 들어오는 사용자 컨트롤러용 — 컨텍스트 주입 + sticky 저장 통합.
     *
     * <p>{@code /survey/{siteCode}}, {@code /bbs/{siteCode}/{bbsCode}}, {@code /airbnb/**} 같은
     * 사이트 명시 진입점에서 호출하면, 같은 세션의 후속 평면 URL 요청({@code /member/login} 등) 도
     * 동일 사이트 레이아웃을 유지한다.
     *
     * <p>{@link #injectByCode}와 달리 sticky 도 함께 갱신되므로 master 식별자 기반 진입점에서는
     * 사용하지 말 것 (master.getSiteCode() 가 valid 한지 호출자가 보장한 뒤 호출).
     */
    public void injectByCodeAndStick(String siteCode, HttpServletRequest req, Model model) {
        if (siteCode == null || siteCode.isBlank()) return;
        log.info("===SITE_CONTEXT inject+stick siteCode={} uri={}", siteCode, req.getRequestURI());
        injectByCode(siteCode, req, model);
        saveSticky(req, siteCode);
    }

    /**
     * {@link #injectByCode(String, Model)} 의 프리뷰 인지 변형 — req 가 있으면 템플릿 프리뷰
     * (?tmpl=CODE, 세션 sticky — {@link TemplatePreview}) 를 적용한 컨텍스트를 주입한다.
     */
    public void injectByCode(String siteCode, HttpServletRequest req, Model model) {
        injectInternal(siteCode, req, model);
    }

    /**
     * 명시적으로 siteCode 를 알고 있는 경우 (예: master.getSiteCode()) 직접 호출.
     * null/blank 면 no-op — 호출자가 별도 폴백 처리.
     *
     * <p>이 경로로 주입된 siteCode 도 session sticky 에 반영하지 않음 — 호출자(master 도메인) 가
     * 자체 식별자(masterId) 로 컨텍스트를 결정하므로 session 영향권 밖.
     */
    public void injectByCode(String siteCode, Model model) {
        injectInternal(siteCode, null, model);
    }

    private void injectInternal(String siteCode, HttpServletRequest req, Model model) {
        if (siteCode == null || siteCode.isBlank()) return;

        SiteContext ctx;
        try {
            ctx = siteContextService.getContextByCode(siteCode);
        } catch (Exception ex) {
            log.warn("SITE_CONTEXT resolve failed siteCode={} reason={}", siteCode, ex.getMessage());
            return;
        }
        if (ctx == null) {
            log.info("SITE_CONTEXT unknown siteCode={}", siteCode);
            return;
        }

        // 템플릿 프리뷰(?tmpl=CODE / ?tmpl=off — 세션 sticky) — req 가 있는 경로만
        if (req != null) {
            ctx = TemplatePreview.apply(ctx, req, siteContextService);
        }

        model.addAttribute("siteContext", ctx);
        model.addAttribute("siteCode",    ctx.getSiteCode());
        model.addAttribute("siteId",      ctx.getSiteId());

        String lp = ctx.getLayoutPath();
        model.addAttribute("layoutPath", (lp != null && !lp.isBlank()) ? lp : EMPTY_LAYOUT);

        siteContextHolder.set(ctx);   // ← DB 계층용. 뷰/DB 컨텍스트가 항상 동일 ctx로 일치

        log.info("===SITE_CONTEXT injected siteCode={} siteId={} layoutPath={}", ctx.getSiteCode(), ctx.getSiteId(), model.getAttribute("layoutPath"));
    }

    /**
     * siteCode 만 단순 해석 — model 주입은 하지 않음. session 폴백 포함.
     * 컨트롤러 핸들러에서 siteCode 만 필요한 경우(예: 비즈니스 로직 필터) 에 사용.
     */
    public String resolveSiteCode(HttpServletRequest req, Model model) {
        String c = resolveExplicitSiteCode(req, model);
        if (c != null) return c;
        return readSticky(req);
    }

    /**
     * session sticky 제거 — 로그아웃 핸들러 / 관리자 사이트 컨텍스트 리셋 등에서 호출.
     */
    public void clearSticky(HttpServletRequest req) {
        HttpSession sess = req.getSession(false);
        if (sess != null) {
            Object prev = sess.getAttribute(SESSION_SITE_CODE);
            sess.removeAttribute(SESSION_SITE_CODE);
            log.info("===SITE_CONTEXT sticky cleared prevSiteCode={}", prev);
        }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /** request param → request attribute → model attribute 만 검사 (session 제외). */
    private static String resolveExplicitSiteCode(HttpServletRequest req, Model model) {
        String c = req.getParameter("siteCode");
        if (isNonBlank(c)) return c.trim();

        Object reqAttr = req.getAttribute("siteCode");
        if (reqAttr instanceof String s && isNonBlank(s)) return s.trim();

        Object modelAttr = (model == null) ? null : model.getAttribute("siteCode");
        if (modelAttr instanceof String s && isNonBlank(s)) return s.trim();

        return null;
    }

    private static String readSticky(HttpServletRequest req) {
        HttpSession sess = req.getSession(false);
        if (sess == null) return null;
        Object v = sess.getAttribute(SESSION_SITE_CODE);
        return (v instanceof String s && isNonBlank(s)) ? s.trim() : null;
    }

    private static void saveSticky(HttpServletRequest req, String siteCode) {
        HttpSession sess = req.getSession(true);
        Object cur = sess.getAttribute(SESSION_SITE_CODE);
        if (!(cur instanceof String s) || !siteCode.equals(s)) {
            sess.setAttribute(SESSION_SITE_CODE, siteCode);
        }
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
