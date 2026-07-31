package com.gonet.config.cache;

import lombok.Getter;

import java.time.Duration;

/**
 * Caffeine 캐시 영역 정의 — 캐시 이름 · 용량 · TTL · 정책 메모의 single source of truth.
 *
 * <p>{@link CacheConfig} 는 본 enum 을 순회하여 {@code CacheManager} 에 등록한다. 신규 캐시
 * 영역 추가 시 본 enum 에 1줄만 더하면 자동 등록 — 별도 builder 작성 불필요.
 *
 * <p><b>호환 표면</b> — {@code @Cacheable(cacheNames = "...")} 는 compile-time 상수만 받으므로
 * {@link CacheConfig} 의 {@code public static final String} 상수는 그대로 유지한다. 본 enum 의
 * {@link #cacheName} 값은 동일 문자열을 가리킨다.
 *
 * <p><b>SITE_CONTEXT 의 TTL</b> — {@code application.yml} 의
 * {@code gopcms.cache.site-context-ttl-minutes} 가 {@link CacheConfig} 빌드 시점에 override 한다.
 * 본 enum 의 값은 기본 fallback.
 */
@Getter
public enum CacheType {

    SITE_CONTEXT      ("siteContext",       500, Duration.ofMinutes(10), Duration.ofMinutes(20),
        "사이트/템플릿/메뉴 통합 컨텍스트 (site_id 키). application.yml 의 site-context-ttl-minutes 가 override."),

    ROLE_URL_ACCESS   ("roleUrlAccess",      10, Duration.ofMinutes(30), null,
        "URL 접근 규칙 (전역 1건, 변경 시 전체 무효화)."),

    CODE_GROUP        ("codeGroup",         200, Duration.ofHours(1),    null,
        "공통코드 그룹/코드 (group_code 키)."),

    ROLE_HIERARCHY    ("roleHierarchy",     500, Duration.ofMinutes(30), null,
        "역할 계층 closure (role_id 키)."),

    MAIL_TEMPLATE     ("mailTemplate",       50, Duration.ofMinutes(30), null,
        "메일 템플릿 (code 키)."),

    ACTIVE_POPUPS     ("activePopups",      500, Duration.ofMinutes(5),  null,
        "사이트별 활성 팝업. 관리자 CUD 시 @CacheEvict(allEntries=true)."),

    ACTIVE_BANNERS    ("activeBanners",     500, Duration.ofMinutes(5),  null,
        "사이트별 활성 배너. 관리자 CUD 시 @CacheEvict(allEntries=true)."),

    SEARCH_POPULAR    ("searchPopular",     500, Duration.ofMinutes(5),  null,
        "인기검색어 TOP-N 자동 집계. 5분 stale 허용."),

    SEARCH_RECOMMEND  ("searchRecommend",   500, Duration.ofMinutes(5),  null,
        "추천검색어 (운영자 수동). 관리자 CUD 시 @CacheEvict."),

    SEARCH_FORBIDDEN  ("searchForbidden",   500, Duration.ofMinutes(5),  null,
        "검색 금지어. 매 검색 요청에서 차단 검사."),

    SEARCH_SYNONYM    ("searchSynonym",     500, Duration.ofMinutes(5),  null,
        "검색 동의어/유의어 사전."),

    WEATHER_STATS     ("weatherStats",     2000, Duration.ofHours(3),    null,
        "날씨 통계 위젯 — 격자(nx,ny) 당 buildStats() 결과. WeatherCollectScheduler 가 @CacheEvict."),

    SECONDARY_CONTRACT("secondaryContract", 2000, Duration.ofHours(3),    null,
        "외부 계약 시스템 뷰(V_TCM_*) read-only. 본 앱이 적재 안 함 — TTL 3시간 자연 만료."),

    SECONDARY_G2B     ("secondaryG2b",     2000, Duration.ofHours(3),    null,
        "조달청 수집 결과. G2bUpsertExecutor / G2bPersistOps 가 적재 직후 @CacheEvict(allEntries=true)."),

    SECONDARY_LFIOS   ("secondaryLfios",   2000, Duration.ofHours(3),    null,
        "재정정보 외부 시스템 뷰 read-only. 본 앱이 적재 안 함 — TTL 3시간 자연 만료.");

    /** Spring Cache 이름 — {@code @Cacheable(cacheNames=...)} 에 그대로 사용. */
    private final String   cacheName;

    /** Caffeine maximumSize — LRU 추출 임계. */
    private final long     maximumSize;

    /** expireAfterWrite TTL. */
    private final Duration expireAfterWrite;

    /** expireAfterAccess TTL — nullable (미적용 시 null). */
    private final Duration expireAfterAccess;

    /** 운영자/리뷰어용 정책 메모. */
    private final String   description;

    CacheType(String cacheName, long maximumSize,
              Duration expireAfterWrite, Duration expireAfterAccess,
              String description) {
        this.cacheName         = cacheName;
        this.maximumSize       = maximumSize;
        this.expireAfterWrite  = expireAfterWrite;
        this.expireAfterAccess = expireAfterAccess;
        this.description       = description;
    }
}
