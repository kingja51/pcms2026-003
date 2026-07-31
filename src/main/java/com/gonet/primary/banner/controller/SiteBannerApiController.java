package com.gonet.primary.banner.controller;

import com.gonet.common.dto.ApiResponse;
import com.gonet.primary.banner.dto.Banner;
import com.gonet.primary.banner.service.BannerService;
import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 사이트 배너 공개 조회 API — {@code GET /api/v1/sites/{siteCode}/banners?location=SUB_HERO}.
 *
 * <p>레이아웃(krds.html)의 서브페이지 상단 비주얼(sub-hero.js) 등 <b>클라이언트 렌더 슬롯</b>이
 * 호출하는 read-only 공개 엔드포인트. 활성(노출 기간 내·use_yn) 배너만, location 별
 * {@value #LIMIT}건 상한(sort_order 순). 서비스 캐시(activeBanners) 재사용이라 요청당 DB hit 없음.
 *
 * <p>접근규칙: {@code /api/v1/sites/*'/banners'} GET PERMIT_ALL — seed_site_api_access.sql.
 */
@Tag(name = "Site Banners (public)",
    description = "사이트 배너 공개 조회 — 레이아웃 클라이언트 슬롯(sub-hero 등)용 read-only.")
@RestController
@RequestMapping("/api/v1/sites")
public class SiteBannerApiController {

    /** location 별 노출 상한 — SiteContextModelAdvice(SSR 주입)와 동일 정책. */
    private static final int LIMIT = 10;

    /** banner_location 코드 형식(BannerSaveForm 과 동일 규칙). */
    private static final Pattern SAFE_LOCATION = Pattern.compile("^[A-Z0-9_]{1,50}$");

    private final SiteContextService siteContextService;
    private final BannerService      bannerService;

    public SiteBannerApiController(SiteContextService siteContextService, BannerService bannerService) {
        this.siteContextService = siteContextService;
        this.bannerService      = bannerService;
    }

    /** 사용자 노출용 배너 뷰 — 내부 컬럼(감사/기간 등) 제외 슬림 직렬화. */
    public record BannerView(String bannerId, String title, String altText,
                             String linkUrl, String linkTarget, String imageUrl, int sortOrder) {
        static BannerView of(Banner b) {
            return new BannerView(b.getBannerId(), b.getBannerTitle(), b.getAltText(),
                b.getLinkUrl(), b.getLinkTarget(),
                b.getImageFileId() != null ? "/fileDown/" + b.getImageFileId() : null,
                b.getSortOrder());
        }
    }

    @Operation(summary = "사이트 활성 배너 목록(위치별)",
        description = "location(BANNER_LOCATION 코드) 의 활성 배너를 sort_order 순으로 최대 10건 반환.")
    @GetMapping("/{siteCode:[a-zA-Z0-9][a-zA-Z0-9_-]{0,29}}/banners")
    public ResponseEntity<ApiResponse<List<BannerView>>> banners(
            @Parameter(description = "사이트 코드", required = true) @PathVariable String siteCode,
            @Parameter(description = "배너 위치 코드 (예: SUB_HERO)", required = true)
            @RequestParam String location) {
        if (location == null || !SAFE_LOCATION.matcher(location).matches()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("location 형식이 올바르지 않습니다."));
        }
        SiteContext ctx = siteContextService.getContextByCode(siteCode);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("사이트를 찾을 수 없습니다."));
        }
        List<Banner> banners = bannerService.findActiveByLocation(ctx.getSiteId())
            .getOrDefault(location, List.of());
        List<BannerView> views = banners.stream()
            .limit(LIMIT)
            .map(BannerView::of)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(views));
    }
}
