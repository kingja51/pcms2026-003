package com.gonet.config.filter;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.IpUtils;
import com.gonet.common.util.JsonUtils;
import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityEventType;
import com.gonet.logging.security.service.SecurityLogger;
import com.gonet.primary.system.login.service.AdminLoginGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * {@code /admin/login} pre-auth IP 화이트리스트 게이트.
 *
 * <p>로그인 성공 후가 아닌 로그인 <b>폼 접근 자체</b>를 허용된 IP 로 제한.
 * tb_admin_allow_ip 에 활성화된 어떤 한 행이라도 client IP 를 허용하면 통과 —
 * 그렇지 않으면 404 응답으로 엔드포인트 존재 자체를 은닉. 로그인 엔드포인트(/admin/login, /admin/login/**) 에만 적용.
 *
 * <p>폴백 동작: 활성 화이트리스트가 0건이면
 * {@code gopcms.security.admin.ip-fallback-allow=true} (기본) 으로 허용 —
 * 초기 세팅 시 전체 잠금 방지. 운영 전환 시 false 로 변경.
 *
 * <p>Audit: 거부 시 {@code LOGIN_DENY} 이벤트 기록 (reason=IP_NOT_ALLOWED_PREAUTH).
 */
@Component
public class AdminLoginIpGateFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginIpGateFilter.class);

    private final AdminLoginGuard guard;
    private final AuditLogger auditLogger;
    private final SecurityLogger securityLogger;

    public AdminLoginIpGateFilter(AdminLoginGuard guard,
                                   AuditLogger auditLogger,
                                   SecurityLogger securityLogger) {
        this.guard = guard;
        this.auditLogger = auditLogger;
        this.securityLogger = securityLogger;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // /admin/login 및 그 하위(/admin/login/2fa 등) 만 가드. 나머지 /admin/** 은 일반 인가 체인.
        return !(uri != null && (uri.equals("/admin/login") || uri.startsWith("/admin/login/") || uri.startsWith("/admin/login?")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        log.info("===AdminLoginIpGateFilter checking IP for URI: {}", req.getRequestURI());
        String clientIp = IpUtils.clientIp(req);
        if (guard.ipAllowedAnyAdmin(clientIp)) {
            log.info("===[AdminLoginIpGate] 관리자 로그인 IP 화이트리스트 검증 통과 - ip={}, uri={}", clientIp, req.getRequestURI());
            chain.doFilter(req, res);
            return;
        }
        log.warn("ADMIN_LOGIN_IP_GATE_DENY ip={} uri={}", clientIp, req.getRequestURI());
        try {
            auditLogger.write(AuditEvent.of("LOGIN_DENY", "tb_admin")
                .withActor(null, null, null)
                .withHttp(req.getMethod(), req.getRequestURI(), AuditContext.ip(), req.getHeader("User-Agent"))
                .withResult("FAIL")
                .withAfter("{\"reason\":\"IP_NOT_ALLOWED_PREAUTH\"}"));
        } catch (Exception ignored) {
            // 감사 기록 실패가 차단 응답을 막지 않도록 무시.
        }
        try {
            // 보안 이벤트 분리 적재 (§0.48) — SOC/SIEM 모니터링 1차 소스
            securityLogger.write(SecurityEvent.of(SecurityEventType.IP_DENY)
                .withDetail("{\"reason\":\"IP_NOT_ALLOWED_PREAUTH\""
                    + ",\"endpoint\":" + JsonUtils.quote(req.getRequestURI()) + "}"));
        } catch (Exception ignored) {
            // 보안 이벤트 적재 실패가 차단 응답을 막지 않도록 무시 — fallback logger 가 흡수.
        }
        res.setHeader("Cache-Control", "no-store");
        // Spring Boot ErrorController 가 templates/error/404.html 을 렌더링하도록 위임.
        // 응답 상태는 404 — endpoint 존재 자체를 은닉.
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
