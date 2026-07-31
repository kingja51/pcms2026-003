package com.gonet.primary.banner.service;

import com.gonet.primary.banner.dto.Banner;
import com.gonet.primary.banner.dto.BannerSaveForm;
import com.gonet.primary.banner.dto.BannerSearch;

import java.util.List;
import java.util.Map;

public interface BannerService {

    List<Banner> search(BannerSearch search);

    int count(BannerSearch search);

    Banner get(String bannerId);

    /**
     * 사이트별 활성 배너를 location 별로 그룹핑한 맵.
     * 레이아웃에서 {@code bannersByLocation['HEADER']} 처럼 직접 접근.
     */
    Map<String, List<Banner>> findActiveByLocation(String siteId);

    String create(BannerSaveForm form);

    void update(BannerSaveForm form);

    void toggleUse(String bannerId, boolean active);

    void softDelete(String bannerId);
}
