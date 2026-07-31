package com.gonet.primary.system.site.controller;

import com.gonet.primary.banner.dto.Banner;
import com.gonet.primary.banner.service.BannerService;
import com.gonet.primary.popup.dto.Popup;
import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextResolver;
import com.gonet.primary.system.site.service.SiteContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모든 Thymeleaf 뷰 렌더링 컨텍스트에 공통 모델 속성 주입.
 *
 * <p>SiteContextInterceptor 제거 이후 — request attribute 기반 SiteContext 해석은
 * 더 이상 수행하지 않는다. layoutPath / siteCode / menuId 등이 필요한 컨트롤러는
 * 자체적으로 해석해 model 에 명시 추가해야 한다.
 *
 * <p>본 advice 는 (a) currentUri 주입, (b) 빈 활성 팝업/배너 컬렉션 주입(관리자/사용자 공통 안전값)
 * 만 담당. 사용자 측 동적 팝업/배너는 후속 PR 에서 컨트롤러별로 명시 주입.
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
@Order(0)
public class SiteContextModelAdvice {

    /** Spring Security 기본 CSRF 파라미터명 — 커스텀 이름을 못 읽었을 때의 방어선. */
    private static final String DEFAULT_CSRF_PARAM = "_csrf";

    /** Kakao Map JS API key — 비어 있으면 지도 스크립트가 로드되지 않으니 운영 시 환경변수 설정 필수. */
    @Value("${gopcms.kakao.map.appkey:}")
    private String kakaoMapAppkey;

    /** Naver Maps JS Client ID (NCP) — Application 등록된 ID. 비어 있으면 SDK 호출 skip. */
    @Value("${gopcms.naver.map.client-id:}")
    private String naverMapClientId;

    /** Google Maps JavaScript API Key — Cloud Console 발급 키. 비어 있으면 SDK 호출 skip. */
    @Value("${gopcms.google.map.api-key:}")
    private String googleMapApiKey;

    /** location 별 노출 배너 수 상한(캐러셀 슬라이드 수 등 — 사이트 기준 각 10). */
    private static final int BANNERS_PER_LOCATION_LIMIT = 10;

    private final SiteContextService  siteContextService;
    private final BannerService       bannerService;

    public SiteContextModelAdvice(SiteContextService siteContextService, BannerService bannerService) {
        this.siteContextService  = siteContextService;
        this.bannerService       = bannerService;
    }

    /**
     * 뷰에서 현재 URI 를 참조하기 위한 모델 속성.
     * Thymeleaf 3.1 / Spring Boot 3 부터 {@code #httpServletRequest} 내장 변수가
     * 보안상 제거되어 직접 접근 불가 → 본 advice 로 주입.
     */
    @ModelAttribute("currentUri")
    public String injectCurrentUri(HttpServletRequest req) {
        return req.getRequestURI();
    }

    /**
     * 페이지네이션 조각({@code fragments/pagination})이 링크를 만들 때 붙이는
     * <b>현재 검색조건 쿼리스트링</b>. {@code page} 만 제거하고 나머지를 URL 인코딩해
     * {@code "&keyword=%EA%B3%B5%EC%A7%80&pageSize=20"} 형태로 돌려준다(없으면 빈 문자열).
     *
     * <p>이걸 두는 이유: Thymeleaf 링크식 {@code @{...(a=1,b=2)}} 은 <b>파라미터 이름이
     * 리터럴이어야 한다</b>. Map 을 펼칠 수 없어서, 조각을 재사용하려면 화면마다
     * 검색조건을 일일이 나열해야 했다(001 의 실제 모습 — 그래서 조각이 없었다).
     * 여기서 한 번 만들어 두면 조각은 {@code ?page=N${pageQuery}} 만 조립하면 된다.
     *
     * <p>Thymeleaf 3.1+ 는 {@code #httpServletRequest} 를 제거해 뷰에서 직접 못 읽는다 —
     * {@code currentUri} 와 같은 이유로 advice 가 주입한다.
     *
     * <p><b>CSRF 토큰은 반드시 제외한다.</b> Spring Security 는 토큰을 읽은 뒤에도
     * 파라미터 맵에서 지우지 않으므로, POST 가 뷰를 직접 렌더하는 경로(검증 실패 시 폼
     * 재렌더 등)에서 그대로 복사하면 <b>모든 페이지 링크 href 에 토큰이 박힌다</b> —
     * Referer 헤더로 외부 유출, 브라우저 히스토리, {@code log_access} 적재.
     * 파라미터 이름은 커스터마이즈될 수 있으므로 요청에 실린 {@link CsrfToken} 에서
     * 실제 이름을 읽고, 없으면 기본값 {@code _csrf} 로 막는다.
     */
    @ModelAttribute("pageQuery")
    public String injectPageQuery(HttpServletRequest req) {
        java.util.Map<String, String[]> params = req.getParameterMap();
        if (params.isEmpty()) return "";

        CsrfToken csrf = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
        String csrfParam = (csrf != null && csrf.getParameterName() != null)
                ? csrf.getParameterName() : DEFAULT_CSRF_PARAM;

        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            if ("page".equals(name)) continue;          // 페이지 번호는 조각이 새로 붙인다
            if (csrfParam.equals(name) || DEFAULT_CSRF_PARAM.equals(name)) continue;
            if (e.getValue() == null) continue;
            for (String v : e.getValue()) {
                if (v == null || v.isBlank()) continue; // 빈 검색조건은 URL 을 더럽히기만 한다
                sb.append('&')
                  .append(java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8))
                  .append('=')
                  .append(java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    /**
     * menuId — controller 가 명시 세팅하지 않은 경우의 폴백.
     * query param {@code menuId} 가 있으면 사용, 없으면 null.
     */
    @ModelAttribute("menuId")
    public String injectMenuId(HttpServletRequest req) {
        String paramMid = req.getParameter("menuId");
        return (paramMid != null && !paramMid.isBlank()) ? paramMid : null;
    }

    /** 테마명 화이트리스트 패턴 ("theme-" 프리픽스 제외 부분). */
    private static final java.util.regex.Pattern SAFE_THEME =
        java.util.regex.Pattern.compile("^[a-z][a-z0-9-]{1,30}$");

    /**
     * KRDS 테마 클래스 — {@code <html th:classappend="${themeClass}">} 로 사용.
     *
     * <p>해석 원천은 {@code tb_site.theme} <b>단일</b> — 관리자 사이트 관리에서 지정
     * (2026-07-07 컬럼 신설, 구 {@code gopcms.site.theme} yml 매핑 폐지.
     * {@code ?theme=} 세션 프리뷰도 2026-07-07 사용자 결정으로 제거 — DB 값만 사용).
     * 사이트 코드는 (1) 세션 sticky({@code PCMS_SITE_CODE}) (2) 경로 첫 세그먼트 순으로 해석.
     * 캐시된 SiteContext 경유라 요청당 DB hit 없음, 변경 시 SiteContextChangedEvent 로 즉시 반영.
     *
     * <p>미지정이면 {@code null} → 기본 브랜드(각 템플릿 CSS의 :root 램프). 순수 조회라 부작용 없음.
     */
    @ModelAttribute("themeClass")
    public String injectThemeClass(HttpServletRequest req) {
        return siteThemeClassFor(resolveSiteCode(req));
    }

    /**
     * tb_site.theme 해석 — 화이트리스트 검증 후 "theme-" 접두로 정규화해 반환.
     * 미지정('' / null)·검증 실패·컨텍스트 해석 실패는 null (테마 미적용, 뷰 안전 폴백).
     */
    private String siteThemeClassFor(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) return null;
        try {
            SiteContext ctx = siteContextService.getContextByCode(siteCode);
            if (ctx == null) return null;
            String theme = ctx.getTheme();
            if (theme == null || theme.isBlank()) return null;
            String name = theme.startsWith("theme-") ? theme.substring("theme-".length()) : theme;
            name = name.toLowerCase();
            if (!SAFE_THEME.matcher(name).matches()) return null;
            return "theme-" + name;
        } catch (Exception ex) {
            return null;
        }
    }

    /** 세션 sticky → 경로 첫 세그먼트 순으로 siteCode 추정 (best-effort, 조회 전용). */
    private String resolveSiteCode(HttpServletRequest req) {
        HttpSession sess = req.getSession(false);
        if (sess != null) {
            Object sticky = sess.getAttribute(SiteContextResolver.SESSION_SITE_CODE);
            if (sticky instanceof String sc && !sc.isBlank()) return sc;
        }
        String uri = req.getRequestURI();
        if (uri != null && uri.length() > 1) {
            int start = 1; // leading '/'
            int end = uri.indexOf('/', start);
            String seg = (end < 0) ? uri.substring(start) : uri.substring(start, end);
            if (!seg.isBlank()) return seg;
        }
        return null;
    }

    /** 사용자 뷰에서 참조하는 활성 팝업 — 기본 빈 리스트. 컨트롤러가 필요 시 덮어쓴다. */
    @ModelAttribute("activePopups")
    public List<Popup> injectActivePopups() {
        return List.of();
    }

    /**
     * 사용자 뷰에서 참조하는 활성 배너 location 맵 — 현재 사이트(세션 sticky → 경로 첫 세그먼트)
     * 기준으로 <b>모든 컨트롤러 뷰</b>에 주입(레이아웃 SUB_HERO·배너 슬롯이 게시판/일정/프로그램
     * 페이지에서도 동작). location 별 상위 {@value #BANNERS_PER_LOCATION_LIMIT}건.
     * 사이트/배너 조회는 각자 캐시라 요청당 DB hit 없음. 해석 실패(관리자 경로 등)는 빈 맵.
     */
    @ModelAttribute("bannersByLocation")
    public Map<String, List<Banner>> injectActiveBanners(HttpServletRequest req) {
        try {
            String siteCode = resolveSiteCode(req);
            if (siteCode == null || siteCode.isBlank()) return Map.of();
            SiteContext ctx = siteContextService.getContextByCode(siteCode);
            if (ctx == null) return Map.of();
            Map<String, List<Banner>> byLocation = bannerService.findActiveByLocation(ctx.getSiteId());
            if (byLocation.isEmpty()) return byLocation;
            Map<String, List<Banner>> capped = new LinkedHashMap<>();
            byLocation.forEach((location, banners) -> capped.put(location,
                    banners.size() > BANNERS_PER_LOCATION_LIMIT
                            ? List.copyOf(banners.subList(0, BANNERS_PER_LOCATION_LIMIT))
                            : banners));
            return capped;
        } catch (Exception ex) {
            return Map.of();   // 뷰 안전 폴백 — 배너 없음으로 렌더
        }
    }

    /**
     * Kakao Map JavaScript API key — 모든 뷰에서 {@code ${kakaoMapAppkey}} 로 참조 가능.
     * 환경변수 {@code PCMS_KAKAO_MAP_APPKEY} 미설정 시 빈 문자열 반환 →
     * map.html 같은 템플릿이 빈 키로 SDK 호출하지 않도록 자체 가드.
     */
    @ModelAttribute("kakaoMapAppkey")
    public String injectKakaoMapAppkey() {
        return kakaoMapAppkey == null ? "" : kakaoMapAppkey;
    }

    /**
     * Naver Maps JS Client ID — 모든 뷰에서 {@code ${naverMapClientId}} 로 참조 가능.
     * 환경변수 {@code PCMS_NAVER_MAP_CLIENT_ID} 미설정 시 빈 문자열 반환.
     */
    @ModelAttribute("naverMapClientId")
    public String injectNaverMapClientId() {
        return naverMapClientId == null ? "" : naverMapClientId;
    }

    /**
     * Google Maps JavaScript API Key — 모든 뷰에서 {@code ${googleMapApiKey}} 로 참조 가능.
     * 환경변수 {@code PCMS_GOOGLE_MAP_API_KEY} 미설정 시 빈 문자열 반환.
     */
    @ModelAttribute("googleMapApiKey")
    public String injectGoogleMapApiKey() {
        return googleMapApiKey == null ? "" : googleMapApiKey;
    }
}
