package com.gonet.config.security;

import com.gonet.common.audit.AuditLogger;
import com.gonet.config.filter.AdminLoginIpGateFilter;
import com.gonet.config.filter.CspNonceFilter;
import com.gonet.config.filter.LoginFormatValidationFilter;
import com.gonet.config.filter.RateLimitFilter;
import com.gonet.config.filter.SuspiciousRequestFilter;
import com.gonet.config.access.DynamicAuthorizationManager;
import com.gonet.primary.system.login.controller.AdminLoginFailureHandler;
import com.gonet.primary.system.login.controller.AdminLoginSuccessHandler;
import com.gonet.primary.system.login.service.AdminUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Spring Security 체인 — 회원/관리자 로그인 폼 분리에 따른 이중 FilterChain.
 *
 * <ul>
 * <li>{@link #adminFilterChain} : URL 매처 {@code /admin/**} (Order 10) —
 * AdminUserDetailsService</li>
 * <li>{@link #memberFilterChain} : URL 매처 {@code /member/**} (Order 20) —
 * MemberUserDetailsService</li>
 * <li>{@link #defaultFilterChain} : 그 외 모든 요청 (Order 100) — 정적/홈/공개 콘텐츠</li>
 * </ul>
 *
 * <p>
 * 모든 체인은 동일한 보안 헤더, CSRF, 의심 요청 필터, CSP nonce, RateLimit, HttpFirewall 을 적용.
 * URL 인가는 {@link DynamicAuthorizationManager} 가 통일된 진입점.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    // ------------------------------------------------------------------
    // URL 상수 — 동일 path 가 securityMatcher / formLogin / logout / permitAll
    // 등 여러 곳에 반복되어 오타·드리프트 위험. 단일 출처로 모음.
    // ------------------------------------------------------------------
    private static final String ADMIN_LOGIN = "/admin/login";
    private static final String ADMIN_LOGIN_WILDCARD = "/admin/login/**";
    private static final String ADMIN_LOGOUT = "/admin/logout";
    private static final String ADMIN_LOGIN_EXPIRED = "/admin/login?expired";
    private static final String ADMIN_LOGIN_LOGOUT = "/admin/login?logout";

    private static final String MEMBER_LOGIN = "/member/login";
    private static final String MEMBER_LOGOUT = "/member/logout";
    private static final String MEMBER_LOGIN_EXPIRED = "/member/login?expired";

    private static final String SESSION_COOKIE = "PCMS_SID";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * 중복 로그인 감지용 in-memory 레지스트리. 관리자/회원 체인이 공유 —
     * CustomUserDetails.equals() 는 (userType + userId) 기준이므로 도메인별
     * 동일 사용자가 다른 브라우저에서 로그인하면 직전 세션이 만료된다.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * 세션 파기 이벤트 publisher — {@link SessionRegistryImpl} 이 세션
     * invalidate/expire 를 감지해 principal 맵을 청소하려면 필수.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // ------------------------------------------------------------------
    // AuthenticationProvider — 폼별 UserDetailsService 주입
    // ------------------------------------------------------------------

    private DaoAuthenticationProvider providerOf(Object userDetailsService, PasswordEncoder enc) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(
                (org.springframework.security.core.userdetails.UserDetailsService) userDetailsService);
        p.setPasswordEncoder(enc);
        p.setHideUserNotFoundExceptions(true);
        return p;
    }

    // ------------------------------------------------------------------
    // 1) Admin chain — /admin/**
    // ------------------------------------------------------------------

    @Bean
    @Order(10)
    public SecurityFilterChain adminFilterChain(
            HttpSecurity http,
            AdminUserDetailsService adminUds,
            PasswordEncoder enc,
            CspNonceFilter cspNonceFilter,
            RateLimitFilter rateLimitFilter,
            SuspiciousRequestFilter suspiciousRequestFilter,
            AdminLoginIpGateFilter adminLoginIpGateFilter,
            LoginFormatValidationFilter loginFormatValidationFilter,
            DynamicAuthorizationManager dynamicAuthz,
            AdminLoginSuccessHandler successHandler,
            AdminLoginFailureHandler failureHandler,
            SessionRegistry sessionRegistry,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {

        http.securityMatcher("/admin/**")
                .authenticationProvider(providerOf(adminUds, enc))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().changeSessionId()
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl(ADMIN_LOGIN_EXPIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ADMIN_LOGIN, ADMIN_LOGIN_WILDCARD).permitAll()
                        // 모든 /admin/** 인가를 DynamicAuthorizationManager(tb_role_url_access) 에 위임.
                        // 하드코딩 "/admin/system/**" 규칙을 두면 tb_role_url_access 의
                        // required_roles(UUID 체크)가 평가되지 않아 세밀 권한 제어가 우회됨.
                        // 미등록 URL 은 DynamicAuthorizationManager 가 DENY(fallback) 처리.
                        .anyRequest().access((authSupplier, ctx) -> {
                            jakarta.servlet.http.HttpServletRequest req = ctx.getRequest();
                            log.info("================= [AdminFilterChain 권한 검사 시작] ==============");
                            log.info("===요청 URL : {} {}", req.getMethod(), req.getRequestURI());
                            
                            org.springframework.security.core.Authentication authItem = authSupplier.get();
                            if (authItem != null && authItem.isAuthenticated() && !"anonymousUser".equals(authItem.getPrincipal())) {
                                if (authItem.getPrincipal() instanceof com.gonet.primary.system.login.dto.CustomUserDetails ud) {
                                    log.info("===사용자 유형 : {}", ud.getUserType());
                                    log.info("===사용자 역할(Role IDs) : {}", ud.getRoleIds());
                                    log.info("===사용자 역할(Role Codes) : {}", ud.getRoleCodes());
                                } else {
                                    log.info("===사용자 Principal : {}", authItem.getPrincipal());
                                }
                            } else {
                                log.info("===사용자 상태 : 비로그인 (Anonymous)");
                            }

                            org.springframework.security.authorization.AuthorizationDecision decision = dynamicAuthz.check(authSupplier, ctx);
                            log.info("===최종 승인 여부 : {}", decision != null && decision.isGranted() ? "GRANTED (통과)" : "DENIED (거부)");
                            log.info("================================================================");
                            return decision;
                        }))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .formLogin(form -> form
                        .loginPage(ADMIN_LOGIN)
                        .loginProcessingUrl(ADMIN_LOGIN)
                        .usernameParameter("loginId")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, ADMIN_LOGOUT))
                        .logoutSuccessUrl(ADMIN_LOGIN_LOGOUT)
                        .deleteCookies(SESSION_COOKIE, CSRF_COOKIE)
                        .invalidateHttpSession(true));

        applyCommonHeaders(http);
        http.addFilterBefore(suspiciousRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cspNonceFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminLoginIpGateFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginFormatValidationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // 2) Member chain — /member/**  ※ P5 에서 복원
    // ------------------------------------------------------------------
    //
    // 001 은 여기에 @Order(20) 회원 체인이 있었다. 003 은 P2 에서 제외한다 —
    // MemberLoginSuccessHandler/FailureHandler 가 MemberMapper(로그인 잠금 카운터)에
    // 의존하는데 회원 도메인은 P5 범위이기 때문이다.
    //
    // 제외해도 /member/** 는 default 체인(Order 100)으로 떨어지고,
    // tb_role_url_access 에 규칙이 없으면 DynamicAuthorizationManager 가 DENY 한다 —
    // 회원 기능이 없는 P2 시점에는 이것이 올바른 동작이다.
    //
    // P5 에서 회원 도메인과 함께 @Order(20) 체인을 복원한다.

    // ------------------------------------------------------------------
    // 3) Default chain — 정적/홈/공개 콘텐츠
    // ------------------------------------------------------------------

    @Bean
    @Order(100)
    public SecurityFilterChain defaultFilterChain(
            HttpSecurity http,
            CspNonceFilter cspNonceFilter,
            RateLimitFilter rateLimitFilter,
            SuspiciousRequestFilter suspiciousRequestFilter,
            DynamicAuthorizationManager dynamicAuthz,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher("/api/v1/public/**"),
                                PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"),
                                // CSP 위반 리포트 — 브라우저가 CSRF 토큰 없이 POST (report-uri/report-to)
                                PathPatternRequestMatcher.withDefaults().matcher("/csp-report")))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().changeSessionId())
                .authorizeHttpRequests(auth -> auth
                        // ── 공개 허용 ──────────────────────────────────────────────────────
                        .requestMatchers(
                                "/",
                                "/css/**", "/js/**", "/images/**", "/img/**", "/tmpl/**",
                                // self-host 웹폰트(Pretendard·IBM Plex) — 누락 시 익명 폰트요청이
                                // 로그인 redirect(302) 되어 @font-face 가 실패, 시스템 폰트로 폴백됨.
                                "/fonts/**",
                                "/favicon.ico", "/robots.txt",
                                "/sw.js", "/.well-known/**",
                                "/error", "/error/**",
                                "/login",
                                "/airbnb", "/airbnb/**",
                                "/clay", "/clay/**",
                                // actuator 중 로드밸런서·모니터링 수집 허용 엔드포인트만 명시
                                "/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        // ── 코드 레벨 관리자 고정 가드 ──────────────────────────────────────
                        // tb_role_url_access 미등록이어도 차단 — DynamicAuthorizationManager 우회 방어.
                        // tb_admin → user_type='STAFF' → CustomUserDetails 가 ROLE_STAFF authority 부여.
                        // 전체 관리자(tb_admin)가 ROLE_STAFF 이므로 hasRole("STAFF") 로 차단.
                        // 규칙 평가 순서: permitAll 먼저 → 아래 hasRole → anyRequest.
                        // 따라서 /actuator/health 는 위 permitAll 에서 이미 처리되어 이 블록 미도달.
                        .requestMatchers(
                                // Swagger / OpenAPI
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                // Actuator — 위 공개 목록 이외 전체 (beans/env/loggers/heapdump 등)
                                "/actuator",
                                "/actuator/beans", "/actuator/conditions",
                                "/actuator/configprops", "/actuator/configprops/**",
                                "/actuator/env", "/actuator/env/**",
                                "/actuator/loggers", "/actuator/loggers/**",
                                "/actuator/heapdump", "/actuator/threaddump",
                                "/actuator/metrics", "/actuator/metrics/**",
                                "/actuator/mappings", "/actuator/scheduledtasks",
                                "/actuator/sessions", "/actuator/caches",
                                "/actuator/shutdown", "/actuator/startup",
                                "/actuator/httpexchanges")
                        .hasRole("ADMIN")
                        // ── 나머지는 DB-RBAC 위임 ───────────────────────────────────────────
                        .anyRequest().access((authSupplier, ctx) -> {
                            org.springframework.security.authorization.AuthorizationDecision decision = dynamicAuthz.check(authSupplier, ctx);
                            // 진단 로그는 DEBUG 로만 — 운영(com.gonet=INFO)에서 매 요청 URL·사용자
                            // 역할이 로그로 흘러 정보 노출·로그 폭주·성능 부담이 되던 것을 차단.
                            // 필요 시 logging.level.com.gonet.config.security=DEBUG 로 활성.
                            if (log.isDebugEnabled()) {
                                jakarta.servlet.http.HttpServletRequest req = ctx.getRequest();
                                org.springframework.security.core.Authentication authItem = authSupplier.get();
                                String who;
                                if (authItem != null && authItem.isAuthenticated()
                                        && !"anonymousUser".equals(authItem.getPrincipal())) {
                                    who = (authItem.getPrincipal() instanceof com.gonet.primary.system.login.dto.CustomUserDetails ud)
                                            ? ud.getUserType() + "/" + ud.getRoleIds()
                                            : String.valueOf(authItem.getPrincipal());
                                } else {
                                    who = "Anonymous";
                                }
                                log.debug("[권한검사] {} {} · user={} · {}",
                                        req.getMethod(), req.getRequestURI(), who,
                                        decision != null && decision.isGranted() ? "GRANTED" : "DENIED");
                            }
                            return decision;
                        }))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        applyCommonHeaders(http);
        http.addFilterBefore(suspiciousRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cspNonceFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ------------------------------------------------------------------
    // 공통 보안 헤더 — 3개 체인 모두 동일 적용
    // ------------------------------------------------------------------

    private static void applyCommonHeaders(HttpSecurity http) throws Exception {
        http.headers(h -> h
                // same-origin iframe (관리자 미리보기 등) 허용. cross-origin 임베드 는 차단.
                // 추가 방어층은 CSP `frame-ancestors 'self'` (CspNonceFilter).
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                .contentTypeOptions(c -> {
                })
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000L))
                .referrerPolicy(r -> r.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .xssProtection(x -> x.headerValue(
                        XXssProtectionHeaderWriter.HeaderValue.DISABLED))
                .addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy",
                        "accelerometer=(), autoplay=(), camera=(), geolocation=(), "
                                + "gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()"))
                // same-origin-allow-popups : 팝업이 cross-origin (예: nice.checkplus.co.kr) 으로
                // navigate 했다가 돌아와도 window.opener 유지. NICE 본인인증·결제 등 외부
                // redirect 흐름이 부모창과 통신하려면 필수. same-origin 으로 두면
                // browsing context group switch 가 발생해 opener 가 null 이 된다.
                .addHeaderWriter(new StaticHeadersWriter(
                        "Cross-Origin-Opener-Policy", "same-origin-allow-popups"))
                // COEP: credentialless — Kakao/Naver Map SDK 등 외부 CDN 이 CORP 헤더 없이도
                // cross-origin 로드되도록 허용. Spectre 방어층은 유지하되 인증정보(cookie)
                // 를 전송하지 않고 fetch — 공개 CDN 에 적합. require-corp 는 모든 외부
                // 리소스가 CORP 헤더 또는 CORS 응답을 보내야 해 third-party 지도 SDK 차단.
                // Browser 지원: Chrome 96+, Edge 96+, Firefox 119+.
                .addHeaderWriter(new StaticHeadersWriter(
                        "Cross-Origin-Embedder-Policy", "credentialless"))
                .addHeaderWriter(new StaticHeadersWriter(
                        "Cross-Origin-Resource-Policy", "same-origin")));
    }

    @Bean
    public org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer webSecurityCustomizer(
            HttpFirewall httpFirewall,
            RequestRejectedHandler requestRejectedHandler) {
        return web -> web
                .httpFirewall(httpFirewall)
                .requestRejectedHandler(requestRejectedHandler);
    }
}
