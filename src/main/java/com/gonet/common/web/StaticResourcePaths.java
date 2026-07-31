package com.gonet.common.web;

/**
 * 정적 리소스/헬스체크/에러 forward 등 "비즈니스 트래픽 아닌" 경로 판정 헬퍼.
 *
 * <p>여러 필터가 같은 정적 경로 prefix/확장자를 중복 정의하지 않도록 단일 진입점.
 * 사용처:
 * <ul>
 *   <li>{@code AccessLogFilter} — log_access INSERT 제외</li>
 *   <li>{@code SuspiciousRequestFilter} — 스캐너 패턴 검사 제외 (정적 prefix 만)</li>
 * </ul>
 */
public final class StaticResourcePaths {

    private StaticResourcePaths() {}

    /** 정적 prefix — `/css/abc.css` 처럼 첫 세그먼트로 시작하는 경로. */
    private static final String[] STATIC_PREFIXES = {
        "/css/", "/js/", "/img/", "/images/", "/tmpl/",
        "/static/", "/fonts/", "/webjars/"
    };

    /** 헬스체크/메트릭 prefix. */
    private static final String[] INFRA_PREFIXES = {
        "/actuator/", "/error"
    };

    /** 단일 파일 경로 — 루트에 위치. */
    private static final String[] ROOT_FILES = {
        "/favicon.ico", "/robots.txt", "/sw.js"
    };

    /** 정적 확장자 (lowercase). */
    private static final String[] STATIC_EXTENSIONS = {
        "css", "js",  "map",
        "png", "jpg", "jpeg", "gif",
        "svg", "webp", "ico",
        "woff", "woff2", "ttf", "otf", "eot"
    };

    /**
     * 비즈니스 트래픽으로 보지 않을 경로인지 — 정적 + 헬스 + 에러 + 루트 파일 + 확장자 매칭.
     */
    public static boolean isStaticOrInfra(String uri) {
        if (uri == null) return true;
        for (String p : STATIC_PREFIXES) if (uri.startsWith(p)) return true;
        for (String p : INFRA_PREFIXES)  if (uri.startsWith(p)) return true;
        for (String f : ROOT_FILES)      if (uri.equals(f))     return true;
        return hasStaticExtension(uri);
    }

    /**
     * 정적 prefix 만 검사 — `SuspiciousRequestFilter` 처럼 헬스체크는 별도 정규식으로
     * 다루는 호출자에서 사용.
     */
    public static boolean hasStaticPrefix(String uri) {
        if (uri == null) return false;
        for (String p : STATIC_PREFIXES) if (uri.startsWith(p)) return true;
        return false;
    }

    /**
     * 정적 prefix 또는 루트 파일(favicon/robots/sw.js) 검사. /actuator 는 포함하지 않으므로
     * 공격 탐지 필터에서 안전하게 사용 가능 (/actuator/env 등은 통과시켜 별도 검사).
     */
    public static boolean hasStaticPrefixOrRootFile(String uri) {
        if (uri == null) return false;
        if (hasStaticPrefix(uri)) return true;
        for (String f : ROOT_FILES) if (uri.equals(f)) return true;
        return false;
    }

    private static boolean hasStaticExtension(String uri) {
        int dot = uri.lastIndexOf('.');
        if (dot <= 0 || dot <= uri.lastIndexOf('/')) return false;
        String ext = uri.substring(dot + 1).toLowerCase();
        for (String e : STATIC_EXTENSIONS) if (e.equals(ext)) return true;
        return false;
    }
}
