package com.gonet.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * IP / Host 추출 공통 유틸.
 *
 * <p>AuditContextFilter / RateLimitFilter / SiteContextInterceptor 에서 공통 사용.
 *
 * <p><b>X-Forwarded-For 위조 방어 (P0 해소, 2026-05-27):</b><br>
 * 이전 구현은 {@code X-Forwarded-For} 등 헤더를 무조건 신뢰했으나, fronting proxy 없이
 * 직접 노출되는 환경에서는 공격자가 임의 IP 를 송신해 admin IP 화이트리스트 / RateLimit /
 * 감사 IP 전부 우회 가능했음.
 *
 * <p>현재 정책:
 * <ol>
 *   <li>{@code req.getRemoteAddr()} 가 {@code gopcms.security.trusted-proxies}
 *       CIDR 화이트리스트에 매칭되면 — {@link #clientIp} 가 {@code X-Forwarded-For}
 *       체인을 <b>우측부터</b> 스캔해 신뢰 프록시 홉을 건너뛴 첫 비신뢰 주소를 채택.
 *       (최좌측 값을 신뢰하면 클라이언트가 위조한 IP 를 그대로 받아들이는 스푸핑에 노출됨)</li>
 *   <li>매칭 안 되면 {@code X-Forwarded-*} 헤더 일체 무시 — {@code RemoteAddr} 만 반환.</li>
 *   <li>기본값(빈 리스트) 일 때 모든 요청은 {@code RemoteAddr} 기반 — 가장 보수적.</li>
 * </ol>
 *
 * <p>운영 가이드:
 * <ul>
 *   <li>Nginx/ALB 뒤 배포 시 — fronting proxy 의 IP CIDR 를 운영 yml/환경변수로 주입
 *       (예: {@code PCMS_TRUSTED_PROXIES=10.0.0.0/8,172.16.0.0/12}). Tomcat 의
 *       {@code server.forward-headers-strategy=native} 와 별개의 방어층으로 동작.</li>
 *   <li>운영 권장: Tomcat connector 를 {@code address=127.0.0.1} 로 바인딩하여
 *       프록시 우회 경로 자체 차단.</li>
 * </ul>
 */
public final class IpUtils {

    public static final String UNKNOWN = "0.0.0.0";

    private static final String[] CLIENT_IP_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_CLIENT_IP",
        "HTTP_X_FORWARDED_FOR"
    };

    /**
     * 신뢰 프록시 CIDR 목록. {@code TrustedProxiesConfig} 가 부팅 시 주입.
     * volatile — 다중 스레드 read 안전, 부팅 후 변경 없음(boot-time only).
     */
    @SuppressWarnings("PMD.RedundantFieldInitializer")
    private static volatile List<String> trustedProxyCidrs = List.of();

    private IpUtils() {}

    /**
     * 신뢰 프록시 CIDR 화이트리스트 주입 — {@code TrustedProxiesConfig} 에서 1회 호출.
     * 운영 중 재호출 가능하지만 테스트/운영 정상 흐름에서는 부팅 시 1회.
     */
    public static void setTrustedProxyCidrs(List<String> cidrs) {
        trustedProxyCidrs = (cidrs == null) ? List.of() : List.copyOf(cidrs);
    }

    /**
     * 현재 신뢰 프록시 CIDR 목록 — 진단/감사용.
     */
    public static List<String> getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    /**
     * 클라이언트 IP 추출.
     *
     * <p>{@code remoteAddr} 가 신뢰 프록시이면 {@code X-Forwarded-For} 등 헤더 신뢰,
     * 아니면 {@code remoteAddr} 만 반환. 모든 값이 없으면 {@link #UNKNOWN} 반환(null 금지).
     */
    public static String clientIp(HttpServletRequest req) {
        if (req == null) return UNKNOWN;
        String remote = req.getRemoteAddr();
        if (isFromTrustedProxy(remote)) {
            for (String h : CLIENT_IP_HEADERS) {
                String v = req.getHeader(h);
                if (!isSet(v)) continue;
                // X-Forwarded-For 는 "client, proxy1, proxy2, ..." 체인이므로 우측부터 스캔.
                // 그 외(X-Real-IP 등)는 신뢰 프록시가 세팅한 단일 값 → 그대로 신뢰.
                String resolved = "X-Forwarded-For".equalsIgnoreCase(h)
                    ? clientFromForwardedChain(v)
                    : firstCsv(v);
                if (isSet(resolved)) return resolved;
            }
        }
        return isSet(remote) ? remote : UNKNOWN;
    }

    /**
     * {@code X-Forwarded-For} 체인에서 실제 클라이언트 IP 추출.
     *
     * <p>XFF 는 좌→우로 오래된 홉 순({@code client, proxy1, proxy2}) 이며, fronting proxy 는
     * 자신이 받은 peer 주소를 <b>우측에 append</b>({@code $proxy_add_x_forwarded_for}) 한다.
     * 따라서 우측부터 신뢰 프록시 홉을 건너뛰고 처음 만나는 비신뢰 주소가 실제 클라이언트다.
     * 클라이언트가 헤더를 위조해도 <b>좌측</b>에만 심을 수 있어 우측 스캔 결과에 영향을 주지 못한다.
     * (이전 구현은 최좌측 값을 신뢰 → 위조된 IP 를 그대로 채택하는 스푸핑 취약점이 있었다.)
     *
     * @return 첫 비신뢰 주소, 체인이 전부 신뢰 프록시면 {@code null}(호출부가 remoteAddr fallback)
     */
    private static String clientFromForwardedChain(String xff) {
        String[] tokens = xff.split(",");
        for (int i = tokens.length - 1; i >= 0; i--) {
            String ip = tokens[i].trim();
            if (!isSet(ip)) continue;
            if (isFromTrustedProxy(ip)) continue;   // 신뢰 프록시 홉 → skip
            return ip;                               // 첫 비신뢰 = 실제 클라이언트
        }
        return null;
    }

    /**
     * {@code remoteAddr} 가 신뢰 프록시 CIDR 화이트리스트에 매칭되는지.
     * 화이트리스트가 비어있으면 항상 false — 헤더 무시 (가장 보수적 기본).
     */
    private static boolean isFromTrustedProxy(String remoteAddr) {
        if (!isSet(remoteAddr)) return false;
        List<String> cidrs = trustedProxyCidrs;
        if (cidrs.isEmpty()) return false;
        for (String cidr : cidrs) {
            if (IpMatcher.matchesCidr(cidr, remoteAddr)) return true;
        }
        return false;
    }

    /**
     * Host(도메인) 추출. X-Forwarded-Host → getServerName 순.
     * 포트/쉼표 분리 제거 후 반환. 매칭 없으면 null.
     */
    public static String host(HttpServletRequest req) {
        if (req == null) return null;
        String xfh = req.getHeader("X-Forwarded-Host");
        String host = isSet(xfh) ? xfh : req.getServerName();
        if (!isSet(host)) return null;
        host = firstCsv(host);
        int colon = host.indexOf(':');
        if (colon > 0) host = host.substring(0, colon);
        return host.trim();
    }

    private static String firstCsv(String v) {
        String s = v.trim();
        int comma = s.indexOf(',');
        return (comma > 0) ? s.substring(0, comma).trim() : s;
    }

    private static boolean isSet(String v) {
        return v != null && !v.isBlank() && !"unknown".equalsIgnoreCase(v);
    }
}
