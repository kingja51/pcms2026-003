package com.gonet.config.filter;

import com.gonet.common.util.IpUtils;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.web.StaticResourcePaths;
import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityEventType;
import com.gonet.logging.security.service.SecurityLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 침해 시도·스캐너 탐지 필터.
 *
 * <p>역할:
 * <ul>
 *   <li>알려진 보안 스캐너 UA 차단 (sqlmap / nikto / nmap / acunetix 등)</li>
 *   <li>명백한 공격 URI 패턴 감지 (/.env, /wp-admin, /phpmyadmin 등)</li>
 *   <li>의심 요청 헤더 검사 (과도하게 긴 Referer/UA, 악성 패턴)</li>
 * </ul>
 *
 * <p>차단 방식: 403 + 로그. {@link com.gonet.config.security.HttpFirewallConfig} 의 StrictHttpFirewall 과 상호보완.
 * <p>관측: 향후 {@code log_security} 테이블에 적재(S1) 하여 이상 탐지 SIEM 에 연동.
 *
 * <p>정적 리소스/헬스체크 제외 (생략 시 프로빙 과탐).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SuspiciousRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SuspiciousRequestFilter.class);

    /** 스캐너 UA 조각 — 소문자 비교 */
    private static final List<String> SCANNER_UA_TOKENS = List.of(
        "sqlmap", "nikto", "nmap", "acunetix", "nessus", "openvas",
        "masscan", "zgrab", "fimap", "w3af", "havij", "morfeus",
        "wpscan", "jaeles", "nuclei", "dirbuster"
    );

    /** 명백한 공격 대상 경로 — CMS 특성상 사용하지 않는 플랫폼 파일 */
    private static final List<Pattern> ATTACK_URI_PATTERNS = List.of(
        Pattern.compile("(?i)/\\.(env|git|svn|htaccess|htpasswd|DS_Store)(/|$)"),
        Pattern.compile("(?i)/(wp-admin|wp-login|wp-content|wp-includes|wordpress)(/|$)"),
        Pattern.compile("(?i)/(phpmyadmin|pma|myadmin|adminer)(/|$)"),
        Pattern.compile("(?i)/(\\.well-known/security|\\.aws/|\\.azure/)"),
        Pattern.compile("(?i)\\.(php|phtml|php3|php4|php5|jsp|jspx|cgi|asp|aspx)(\\?|$)"),
        // c99/r57 substring 매칭은 UUID hex (예: fc994bb7) false-positive 유발 — 제거.
        // 실제 c99.php / r57.txt 류는 위 .(php|jsp|...) 패턴이 이미 차단.
        Pattern.compile("(?i)(shell\\.|cmd\\.exe|webshell)"),
        Pattern.compile("(?i)/cgi-bin/"),
        Pattern.compile("(?i)/HNAP1"),               // 라우터 공격
        Pattern.compile("(?i)/manager/html"),         // Tomcat manager
        Pattern.compile("(?i)/actuator/(env|heapdump|threaddump|beans)") // Actuator 노출 차단
    );

    /**
     * 헬스/에러 forward — 로깅·검사 제외.
     * 정적 리소스 prefix(css/js/images 등) 는 {@link StaticResourcePaths#hasStaticPrefix} 가 담당.
     * /actuator 는 health/info/prometheus 만 안전 — env/heapdump/threaddump/beans 등은
     * {@link #ATTACK_URI_PATTERNS} 에서 명시 차단해야 하므로 prefix 단순 제외 금지.
     */
    private static final List<Pattern> EXCLUDED_PATHS = List.of(
        Pattern.compile("^/actuator/(health|info|prometheus)"),
        Pattern.compile("^/error")
    );

    private static final int MAX_HEADER_LENGTH = 4096;

    private final SecurityLogger securityLogger;

    public SuspiciousRequestFilter(SecurityLogger securityLogger) {
        this.securityLogger = securityLogger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        log.info("===SuspiciousRequestFilter examining URI: {}", req.getRequestURI());
        String uri = req.getRequestURI();

        if (isExcluded(uri)) {
            chain.doFilter(req, res);
            return;
        }

        // 1) 스캐너 UA
        String ua = req.getHeader("User-Agent");
        String uaLower = ua == null ? "" : ua.toLowerCase();
        for (String token : SCANNER_UA_TOKENS) {
            if (uaLower.contains(token)) {
                reject(req, res, "scanner_ua", token);
                return;
            }
        }

        // 2) 공격 URI 패턴
        for (Pattern p : ATTACK_URI_PATTERNS) {
            if (p.matcher(uri).find()) {
                reject(req, res, "attack_uri", p.pattern());
                return;
            }
        }

        // 3) 과도하게 긴 헤더 (HTTP 밀수 등)
        if (ua != null && ua.length() > MAX_HEADER_LENGTH) {
            reject(req, res, "oversized_ua", "len=" + ua.length());
            return;
        }
        String referer = req.getHeader("Referer");
        if (referer != null && referer.length() > MAX_HEADER_LENGTH) {
            reject(req, res, "oversized_referer", "len=" + referer.length());
            return;
        }

        chain.doFilter(req, res);
    }

    private void reject(HttpServletRequest req, HttpServletResponse res, String reason, String detail)
            throws IOException {
        log.warn("SECURITY_SUSPICIOUS reason={} detail={} method={} uri={} ip={} ua={}",
            reason,
            sanitize(detail),
            req.getMethod(),
            sanitize(req.getRequestURI()),
            IpUtils.clientIp(req),
            sanitize(req.getHeader("User-Agent")));
        // §0.48 후속 — SSRF/스캐너 차단 적재 (severity=WARN, 텔레그램은 min-severity=WARN 시 알림)
        try {
            securityLogger.write(SecurityEvent.of(SecurityEventType.SSRF_BLOCK)
                .withDetail("{\"reason\":" + JsonUtils.quote(reason)
                    + ",\"detail\":"    + JsonUtils.quote(sanitize(detail))
                    + ",\"method\":"    + JsonUtils.quote(req.getMethod()) + "}"));
        } catch (Exception ignored) {
            // fallback logger 가 흡수 — 차단 흐름은 그대로
        }
        res.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden");
    }

    private boolean isExcluded(String uri) {
        if (uri == null) return true;
        // 정적 prefix + 루트 파일(favicon/robots/sw.js) — 공격 탐지에서 제외.
        // /actuator 는 일부 path 만 안전(health/info/prometheus) — EXCLUDED_PATHS 정규식이 책임.
        if (StaticResourcePaths.hasStaticPrefixOrRootFile(uri)) return true;
        for (Pattern p : EXCLUDED_PATHS) {
            if (p.matcher(uri).find()) return true;
        }
        return false;
    }

    private String sanitize(String s) {
        if (s == null) return "-";
        String t = s.replace('\n', ' ').replace('\r', ' ');
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }
}
