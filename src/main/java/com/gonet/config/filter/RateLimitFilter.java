package com.gonet.config.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gonet.common.util.IpUtils;
import com.gonet.common.util.JsonUtils;
import com.gonet.config.security.SecurityProperties;
import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityEventType;
import com.gonet.logging.security.service.SecurityLogger;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Rate Limit 필터 (Bucket4j, in-memory + Caffeine eviction).
 *
 * <p>정책:
 * <ul>
 *   <li>POST /admin/login · /member/login  : <b>IP 버킷</b>(IP 당 분당 N) AND <b>loginId 버킷</b>(ID 당 분당 M).
 *       분산 IP 공격으로 IP 단일 키가 우회되는 것을 loginId 키가 2차 방어. 둘 중 하나라도 소진되면 429.</li>
 *   <li>/api/**                            : IP 분당 K 회</li>
 * </ul>
 *
 * <p>버킷 저장소는 Caffeine 캐시 — {@code expireAfterAccess(15분)} + {@code maximumSize(50_000)}.
 * 무한 IP/loginId 키 입력에 의한 메모리 무한 증가(DoS 벡터) 차단. 만료된 버킷은 LRU 로 자동 evict
 * 되며 재생성 시 새 토큰 한도부터 시작 — 정상 사용자 영향 없음.
 *
 * <p>loginId 정규화: {@link #normalize(String)} 가 trim + lowercase. DB collation 이
 * {@code _ai_ci} (현재 MySQL 8.4 utf8mb4_0900_ai_ci) 와 일치 — "Admin" / "admin" 이 같은 사용자.
 * {@code _bin}/{@code _cs} collation 운영 시 다른 사용자가 단일 버킷 공유 → 한 명 공격이 다른 사용자
 * 잠금 위험. 그 경우 본 필터의 {@link #normalize}{@code .toLowerCase()} 제거 필요.
 *
 * <p>단일 인스턴스 전제. 다중 인스턴스 배포 시 Bucket4j Distributed(Redis) 로 교체.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final PathMatcher MATCHER = new AntPathMatcher();

    private static final Set<String> LOGIN_URIS = Set.of("/admin/login", "/member/login");

    /** 버킷 캐시 TTL — 마지막 사용 후 N 분 미사용 시 evict. 정상 사용자에 영향 없는 값. */
    private static final Duration BUCKET_IDLE_TTL = Duration.ofMinutes(15);

    /** 버킷 캐시 최대 크기 — 무한 키 입력 방어. 운영 트래픽에 따라 조정. */
    private static final long BUCKET_MAX_SIZE = 50_000L;

    private final SecurityProperties props;
    private final SecurityLogger     securityLogger;

    private final Cache<String, Bucket> loginIpBuckets;
    private final Cache<String, Bucket> loginUserBuckets;
    private final Cache<String, Bucket> apiBuckets;

    public RateLimitFilter(SecurityProperties props, SecurityLogger securityLogger) {
        this.props = props;
        this.securityLogger = securityLogger;
        this.loginIpBuckets   = newBucketCache();
        this.loginUserBuckets = newBucketCache();
        this.apiBuckets       = newBucketCache();
    }

    private static Cache<String, Bucket> newBucketCache() {
        return Caffeine.newBuilder()
            .expireAfterAccess(BUCKET_IDLE_TTL)
            .maximumSize(BUCKET_MAX_SIZE)
            .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        log.info("===RateLimitFilter evaluating URI: {}", req.getRequestURI());
        String uri = req.getRequestURI();
        String method = req.getMethod();
        String ip = IpUtils.clientIp(req);

        if ("POST".equalsIgnoreCase(method) && LOGIN_URIS.contains(uri)) {
            // 1차: IP 키
            Bucket ipBucket = loginIpBuckets.get(ip, k -> loginIpBucket());
            ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
            if (!ipProbe.isConsumed()) {
                logRateLimit("LOGIN_IP", ip, uri, ipProbe);
                reject(res, ipProbe);
                return;
            }

            // 2차: loginId 키 (비어 있으면 스킵 — 형식 검증 필터가 반려함)
            String loginId = normalize(req.getParameter("loginId"));
            if (loginId != null) {
                Bucket userBucket = loginUserBuckets.get(loginId, k -> loginUserBucket());
                ConsumptionProbe userProbe = userBucket.tryConsumeAndReturnRemaining(1);
                if (!userProbe.isConsumed()) {
                    logRateLimit("LOGIN_USER", loginId, uri, userProbe);
                    reject(res, userProbe);
                    return;
                }
                res.setHeader("X-RateLimit-Remaining",
                    String.valueOf(Math.min(ipProbe.getRemainingTokens(), userProbe.getRemainingTokens())));
            } else {
                res.setHeader("X-RateLimit-Remaining", String.valueOf(ipProbe.getRemainingTokens()));
            }

            chain.doFilter(req, res);
            return;
        }

        if (MATCHER.match("/api/**", uri)) {
            Bucket apiBucket = apiBuckets.get(ip, k -> apiBucket());
            ConsumptionProbe probe = apiBucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                logRateLimit("API_IP", ip, uri, probe);
                reject(res, probe);
                return;
            }
            res.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        }

        chain.doFilter(req, res);
    }

    private static String normalize(String loginId) {
        if (loginId == null) return null;
        String s = loginId.trim().toLowerCase();
        return s.isEmpty() ? null : s;
    }

    private static void reject(HttpServletResponse res, ConsumptionProbe probe) throws IOException {
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After",
            String.valueOf(Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L)));
        res.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
    }

    /**
     * §0.48 후속 — RATE_LIMIT_BLOCK 적재 (severity=INFO, 기본 텔레그램 미알림).
     *
     * <p>{@code limitType} ∈ (LOGIN_IP, LOGIN_USER, API_IP). {@code key} 는 IP 또는 loginId.
     * 적재 실패는 try/catch 로 흡수 — 차단 응답 자체를 막지 않는다.
     */
    private void logRateLimit(String limitType, String key, String uri, ConsumptionProbe probe) {
        try {
            long retryAfterSec = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            securityLogger.write(SecurityEvent.of(SecurityEventType.RATE_LIMIT_BLOCK)
                .withDetail("{\"limitType\":" + JsonUtils.quote(limitType)
                    + ",\"key\":"           + JsonUtils.quote(key)
                    + ",\"uri\":"           + JsonUtils.quote(uri)
                    + ",\"retryAfterSec\":" + retryAfterSec + "}"));
        } catch (Exception ignored) {
            // fallback logger 가 흡수 — 차단 흐름은 그대로
        }
    }

    private Bucket loginIpBucket() {
        int n = props.getRateLimit().getLoginPerIpPerMinute();
        return Bucket.builder()
            .addLimit(Bandwidth.classic(n, Refill.intervally(n, Duration.ofMinutes(1))))
            .build();
    }

    private Bucket loginUserBucket() {
        int n = props.getRateLimit().getLoginPerUserPerMinute();
        return Bucket.builder()
            .addLimit(Bandwidth.classic(n, Refill.intervally(n, Duration.ofMinutes(1))))
            .build();
    }

    private Bucket apiBucket() {
        int n = props.getRateLimit().getApiPerIpPerMinute();
        return Bucket.builder()
            .addLimit(Bandwidth.classic(n, Refill.intervally(n, Duration.ofMinutes(1))))
            .build();
    }
}
