package com.gonet.config.security;

import com.gonet.common.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import java.util.Set;

/**
 * Spring Security HttpFirewall 커스터마이징.
 *
 * <p>{@link StrictHttpFirewall} 은 기본적으로:
 * <ul>
 *   <li>Path traversal ({@code ..}, {@code .}, null byte, 중복 슬래시)</li>
 *   <li>URL encoded slash/backslash</li>
 *   <li>Semicolon/control chars</li>
 *   <li>메서드 화이트리스트</li>
 * </ul>
 * 를 자동 차단. 본 설정은:
 * <ol>
 *   <li>허용 HTTP 메서드 축소 (TRACE/TRACK/CONNECT 제거)</li>
 *   <li>거부 이벤트를 {@code log_security} 분석에 활용 가능하도록 구조화 로그 출력</li>
 *   <li>403 응답 + traceId 헤더 (디버깅 편의)</li>
 * </ol>
 */
@Configuration
public class HttpFirewallConfig {

    private static final Logger log = LoggerFactory.getLogger(HttpFirewallConfig.class);

    /**
     * 허용 HTTP 메서드 — RFP(IBS 등 공공기관) 컴플라이언스로 PUT/PATCH/DELETE 와이어 차단.
     *
     * <p>HTML 폼의 삭제/수정 시멘틱은 {@code spring.mvc.hiddenmethod.filter.enabled=true}
     * 로 활성화된 {@link org.springframework.web.filter.HiddenHttpMethodFilter} 가
     * 와이어 POST + {@code _method=delete|put|patch} 를 컨트롤러 단에서 DELETE/PUT/PATCH
     * 로 변환. 따라서 controller 의 {@code @DeleteMapping}/{@code @PutMapping} 은 그대로 동작.
     *
     * <p>진짜 DELETE/PUT/PATCH 와이어가 필요한 신규 REST 엔드포인트가 생기면
     * 그 시점에 정책 재검토. 현재 코드베이스는 100% _method 트릭 사용.
     *
     * <p>TRACE/TRACK/CONNECT 는 공격 벡터 (Cross-Site Tracing 등) — 영구 제외.
     */
    private static final Set<String> ALLOWED_METHODS = Set.of(
        "GET", "POST", "HEAD", "OPTIONS"
    );

    @Bean
    public HttpFirewall strictHttpFirewall() {
        StrictHttpFirewall fw = new StrictHttpFirewall();
        fw.setAllowedHttpMethods(ALLOWED_METHODS);
        fw.setAllowSemicolon(false);         // ;jsessionid 류 차단
        fw.setAllowUrlEncodedSlash(false);   // %2F 차단 (기본값이나 명시)
        fw.setAllowUrlEncodedDoubleSlash(false);
        fw.setAllowUrlEncodedPercent(false);
        fw.setAllowUrlEncodedPeriod(false);
        fw.setAllowBackSlash(false);
        fw.setAllowNull(false);
        return fw;
    }

    /**
     * 차단 시 로그 보강 후 403 반환.
     * 운영에서는 이 이벤트를 {@code log_security} 로 자동 적재(S1 로깅 다중화).
     */
    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return (req, res, ex) -> {
            logRejection(req, ex);
            res.sendError(HttpStatus.FORBIDDEN.value(), "Request rejected by security firewall");
        };
    }

    private void logRejection(HttpServletRequest req, RequestRejectedException ex) {
        log.warn("SECURITY_REJECT reason={} method={} uri={} query={} ip={} ua={}",
            ex.getMessage(),
            safe(req.getMethod()),
            safe(req.getRequestURI()),
            safe(req.getQueryString()),
            IpUtils.clientIp(req),
            safe(req.getHeader("User-Agent")));
    }

    private String safe(String s) {
        if (s == null) return "-";
        // 로그 인젝션 방지 — CR/LF 제거, 길이 제한
        String trimmed = s.replace('\n', ' ').replace('\r', ' ');
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
    }
}
