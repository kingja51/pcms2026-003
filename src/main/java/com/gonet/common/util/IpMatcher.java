package com.gonet.common.util;

/**
 * 화이트리스트용 IPv4 매칭 유틸 — SINGLE / RANGE / CIDR 3종.
 *
 * <p>관리자 로그인 IP 화이트리스트(tb_admin_allow_ip) 와 사이트 정책의 ip_whitelist CSV
 * (CIDR) 양쪽에서 사용한다. 공통 자원에 두어 도메인별 중복 구현을 막는다.
 *
 * <p>IPv6 미지원 — 요구사항 발생 시 별도 메서드로 확장.
 */
public final class IpMatcher {

    private IpMatcher() {}

    /** {@code SINGLE} : ipAddress 와 정확히 일치. */
    public static boolean matchesSingle(String ipAddress, String clientIp) {
        if (isBlank(ipAddress) || isBlank(clientIp)) return false;
        return normalize(ipAddress).equals(normalize(clientIp));
    }

    /** {@code RANGE} : start ≤ clientIp ≤ end (IPv4 long). */
    public static boolean matchesRange(String start, String end, String clientIp) {
        if (isBlank(start) || isBlank(end) || isBlank(clientIp)) return false;
        long s = toLongIPv4(start);
        long e = toLongIPv4(end);
        long c = toLongIPv4(clientIp);
        if (s < 0 || e < 0 || c < 0) return false;
        if (s > e) { long t = s; s = e; e = t; }
        return c >= s && c <= e;
    }

    /** {@code CIDR} : prefix 마스크 일치 (예: "10.0.0.0/8"). */
    public static boolean matchesCidr(String cidr, String clientIp) {
        if (isBlank(cidr) || isBlank(clientIp)) return false;
        int slash = cidr.indexOf('/');
        if (slash <= 0) return false;
        String network = cidr.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(cidr.substring(slash + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (prefix < 0 || prefix > 32) return false;

        long net  = toLongIPv4(network);
        long ip   = toLongIPv4(clientIp);
        if (net < 0 || ip < 0) return false;

        long mask = (prefix == 0) ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (net & mask) == (ip & mask);
    }

    /** IPv4 문자열을 32-bit unsigned long 으로 변환. 실패 시 -1. */
    public static long toLongIPv4(String ip) {
        if (isBlank(ip)) return -1L;
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) return -1L;
        long out = 0L;
        try {
            for (String p : parts) {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return -1L;
                out = (out << 8) | n;
            }
        } catch (NumberFormatException e) {
            return -1L;
        }
        return out;
    }

    private static String normalize(String ip) {
        return ip == null ? "" : ip.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
