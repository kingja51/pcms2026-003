package com.gonet.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Caffeine L1 캐시 설정 (앱 인스턴스 로컬).
 *
 * <p>모든 캐시 영역의 이름·용량·TTL 은 {@link CacheType} enum 이 단일 source of truth.
 * 본 클래스는 enum 을 순회하여 {@link CacheManager} 에 등록한다.
 *
 * <p><b>호환 표면</b> — {@code @Cacheable(cacheNames = CacheConfig.SITE_CONTEXT)} 는
 * compile-time 상수를 요구하므로 {@code public static final String} 상수를 enum 의 cacheName
 * 과 동일 문자열로 유지한다. 신규 캐시 추가 시 (1) {@link CacheType} 에 enum 상수 1줄
 * (2) 본 클래스에 같은 이름의 String 상수 1줄 — 두 곳만 추가.
 *
 * <p>관리자 CUD 시 해당 캐시를 {@code @CacheEvict} 로 무효화 + ApplicationEvent 발행.
 */
@Configuration
public class CacheConfig {

    // ── @Cacheable 어노테이션 호환용 상수 (compile-time constant 필수) ─────────
    public static final String SITE_CONTEXT       = "siteContext";
    public static final String ROLE_URL_ACCESS    = "roleUrlAccess";
    public static final String CODE_GROUP         = "codeGroup";
    public static final String ROLE_HIERARCHY     = "roleHierarchy";
    public static final String MAIL_TEMPLATE      = "mailTemplate";
    public static final String ACTIVE_POPUPS      = "activePopups";
    public static final String ACTIVE_BANNERS     = "activeBanners";
    public static final String SEARCH_POPULAR     = "searchPopular";
    public static final String SEARCH_RECOMMEND   = "searchRecommend";
    public static final String SEARCH_FORBIDDEN   = "searchForbidden";
    public static final String SEARCH_SYNONYM     = "searchSynonym";
    public static final String WEATHER_STATS      = "weatherStats";
    public static final String SECONDARY_CONTRACT = "secondaryContract";
    public static final String SECONDARY_G2B      = "secondaryG2b";
    public static final String SECONDARY_LFIOS    = "secondaryLfios";

    /** SITE_CONTEXT 의 TTL 외부 설정 — enum 의 기본값(10분) 을 override. */
    @Value("${gopcms.cache.site-context-ttl-minutes:10}")
    private long siteContextTtlMinutes;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager mgr = new SimpleCacheManager();
        List<CaffeineCache> caches = new ArrayList<>(CacheType.values().length);
        for (CacheType t : CacheType.values()) {
            caches.add(new CaffeineCache(t.getCacheName(), buildSpec(t)));
        }
        mgr.setCaches(caches);
        return mgr;
    }

    /**
     * enum 1개당 Caffeine 1개 빌드.
     * SITE_CONTEXT 만 외부 설정({@code site-context-ttl-minutes}) 으로 TTL override —
     * 다른 영역은 enum 정의값 그대로.
     */
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildSpec(CacheType t) {
        Caffeine<Object, Object> b = Caffeine.newBuilder()
            .maximumSize(t.getMaximumSize())
            .recordStats();

        if (t == CacheType.SITE_CONTEXT) {
            b.expireAfterWrite (Duration.ofMinutes(siteContextTtlMinutes))
             .expireAfterAccess(Duration.ofMinutes(siteContextTtlMinutes * 2));
        } else {
            b.expireAfterWrite(t.getExpireAfterWrite());
            if (t.getExpireAfterAccess() != null) {
                b.expireAfterAccess(t.getExpireAfterAccess());
            }
        }
        return b.build();
    }
}
