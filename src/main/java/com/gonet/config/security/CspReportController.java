package com.gonet.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSP 위반 리포트 수집 엔드포인트.
 *
 * <p>{@link com.gonet.config.filter.CspNonceFilter} 가 발급하는 정책의
 * {@code report-to}/{@code report-uri} 가 이 경로를 가리킨다. 브라우저는 두 형식으로 POST 한다:
 * <ul>
 *   <li>레거시 {@code report-uri} → {@code application/csp-report} (단일 객체)</li>
 *   <li>Reporting API {@code report-to} → {@code application/reports+json} (배열)</li>
 * </ul>
 * content-negotiation/415 우회를 위해 본문을 InputStream 으로 직접 읽는다.
 *
 * <p>비로그인 브라우저도 POST 하므로 {@link PublicEndpoint} 로 인가 우회.
 * CSRF 토큰을 모르므로 {@code SecurityConfig} 기본 체인에서 {@code /csp-report} 를 CSRF 예외 처리한다.
 *
 * <p>로그 폭주 방지: 동일 본문은 1회만 기록하고 누적 키 수에 상한을 둔다.
 * 수집된 {@code [CSP-VIOLATION]} 로그의 {@code violated-directive}/{@code blocked-uri} 로
 * 'unsafe-inline' 등 완화 요소의 제거 가능 시점을 판단한다.
 */
@RestController
@PublicEndpoint
public class CspReportController {

    private static final Logger log = LoggerFactory.getLogger(CspReportController.class);

    /** 본문 로그 최대 길이. */
    private static final int MAX_BODY_LEN = 2_000;

    /** 중복 억제 키 누적 상한 — 초과 시 추가 로깅 중단(폭주 방지). */
    private static final int MAX_SEEN = 2_000;

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    @PostMapping("/csp-report")
    public ResponseEntity<Void> report(HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!body.isEmpty()) {
            if (body.length() > MAX_BODY_LEN) {
                body = body.substring(0, MAX_BODY_LEN) + "…(truncated)";
            }
            // 동일 위반은 1회만, 그리고 전체 키 수 상한까지만 기록
            if (seen.size() < MAX_SEEN && seen.add(body)) {
                log.warn("[CSP-VIOLATION] {}", body);
            }
        }
        return ResponseEntity.noContent().build();
    }
}
