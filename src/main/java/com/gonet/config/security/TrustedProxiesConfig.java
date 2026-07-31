package com.gonet.config.security;

import com.gonet.common.util.IpUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 신뢰 프록시 CIDR 목록을 {@link IpUtils} 에 주입.
 *
 * <p>설정 키: {@code gopcms.security.trusted-proxies} (CSV) — 예:
 * {@code 10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}.
 *
 * <p>기본값 빈 문자열 — {@link IpUtils#clientIp} 가 모든 {@code X-Forwarded-*} 헤더를
 * 무시하고 {@code RemoteAddr} 만 반환 (가장 보수적). 운영 환경별로 fronting proxy 의
 * 실제 CIDR 를 환경변수 {@code PCMS_TRUSTED_PROXIES} 로 주입.
 *
 * <p>운영 가이드:
 * <ul>
 *   <li>AWS ALB — VPC 사설 CIDR (예: 10.0.0.0/16)</li>
 *   <li>Nginx 동일 호스트 — 127.0.0.1/32</li>
 *   <li>Cloudflare — Cloudflare 공식 IP 범위 (수시 갱신, 운영 가이드 별건)</li>
 * </ul>
 */
@Configuration
public class TrustedProxiesConfig {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxiesConfig.class);

    private final String csv;

    public TrustedProxiesConfig(@Value("${gopcms.security.trusted-proxies:}") String csv) {
        this.csv = csv == null ? "" : csv.trim();
    }

    @PostConstruct
    public void apply() {
        List<String> cidrs = Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
        IpUtils.setTrustedProxyCidrs(cidrs);
        if (cidrs.isEmpty()) {
            log.warn("TRUSTED_PROXIES empty — X-Forwarded-* 헤더 무시, RemoteAddr 만 신뢰. "
                + "fronting proxy 환경에서 운영 시 gopcms.security.trusted-proxies 설정 필요.");
        } else {
            log.info("TRUSTED_PROXIES configured cidrs={}", cidrs);
        }
    }
}
