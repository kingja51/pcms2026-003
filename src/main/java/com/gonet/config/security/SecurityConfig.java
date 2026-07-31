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
import com.gonet.primary.system.login.controller.MemberLoginFailureHandler;
import com.gonet.primary.system.login.controller.MemberLoginSuccessHandler;
import com.gonet.primary.system.login.controller.MemberLogoutSuccessHandler;
import com.gonet.primary.system.login.service.MemberUserDetailsService;
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
    // 2) Member chain — /member/**  (P5 에서 복원, 2026-07-31)
    // ------------------------------------------------------------------

    /**
     * 회원 체인 — {@code /member/**}. P2 에서 미뤘던 것을 회원 도메인과 함께 되살린다.
     *
     * <p>관리자 체인과 다른 점:
     * <ul>
     *   <li><b>2FA 없음</b> — 회원은 TOTP 를 쓰지 않는다({@code v_user_login} 의
     *       {@code two_factor_enabled_yn} 이 회원 갈래에서 상수 {@code 'N'})</li>
     *   <li><b>IP 게이트 없음</b> — {@code adminLoginIpGateFilter} 를 걸지 않는다.
     *       회원은 임의 망에서 접속한다</li>
     *   <li><b>NICE 콜백 CSRF 예외</b> — 본인인증 결과는 외부 도메인이 POST 하므로
     *       토큰을 모른다. 예외는 {@code /member/identity/nice/success|fail} <b>두 경로뿐</b>이며
     *       위조 대비는 NICE 암호문 복호화 검증이 담당한다</li>
     *   <li><b>{@code maxSessionsPreventsLogin(false)}</b> — 새 로그인이 이전 세션을 끊는다.
     *       관리자와 달리 "먼저 로그인한 쪽 우선" 이면 사용자가 스스로 잠기는 사고가 난다</li>
     * </ul>
     *
     * <p>permitAll 경로는 <b>인증 없이 도달해야만 하는 것</b>만 연다 — 로그인·가입·
     * 아이디/비밀번호 찾기·휴면 해제·소셜 콜백·본인인증. 나머지는 전부
     * {@link DynamicAuthorizationManager} 가 DB 규칙으로 판정하고 <b>무매칭이면 DENY</b> 다.
     * 휴면 해제({@code /member/dormant/**})가 permitAll 인 것은 <b>휴면 계정은 로그인할 수
     * 없기 때문</b>이다 — 인증을 요구하면 해제 자체가 불가능해진다. 대신 그 안에서
     * 실명인증 또는 이메일 OTP 로 본인을 확인한다(개발가이드 §10-6).
     */
    @Bean
    @Order(20)
    public SecurityFilterChain memberFilterChain(
            HttpSecurity http,
            MemberUserDetailsService memberUds,
            PasswordEncoder enc,
            CspNonceFilter cspNonceFilter,
            RateLimitFilter rateLimitFilter,
            SuspiciousRequestFilter suspiciousRequestFilter,
            LoginFormatValidationFilter loginFormatValidationFilter,
            DynamicAuthorizationManager dynamicAuthz,
            MemberLoginSuccessHandler successHandler,
            MemberLoginFailureHandler failureHandler,
            MemberLogoutSuccessHandler logoutSuccessHandler,
            SessionRegistry sessionRegistry,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {

        http.securityMatcher("/member/**")
                .authenticationProvider(providerOf(memberUds, enc))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // NICE CheckPlus 콜백 — 외부 도메인이 POST/GET 하므로 CSRF 토큰을 모른다.
                        // 위조 방어는 NICE 암호문 복호화 검증이 대신한다.
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher("/member/identity/nice/success"),
                                PathPatternRequestMatcher.withDefaults().matcher("/member/identity/nice/fail")))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().changeSessionId()
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl(MEMBER_LOGIN_EXPIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(MEMBER_LOGIN, "/member/join", "/member/join/**",
                                "/member/find/**", "/member/dormant/**",
                                "/member/oauth2/**",
                                "/member/identity/nice", "/member/identity/nice/**")
                        .permitAll()
                        .anyRequest().access((authSupplier, ctx) -> {
                            jakarta.servlet.http.HttpServletRequest req = ctx.getRequest();
                            log.info("================= [MemberFilterChain 권한 검사 시작] ==============");
                            log.info("===요청 URL : {} {}", req.getMethod(), req.getRequestURI());

                            org.springframework.security.core.Authentication authItem = authSupplier.get();
                            if (authItem != null && authItem.isAuthenticated()
                                    && !"anonymousUser".equals(authItem.getPrincipal())) {
                                if (authItem.getPrincipal() instanceof com.gonet.primary.system.login.dto.CustomUserDetails ud) {
                                    log.info("===사용자 유형 : {}", ud.getUserType());
                                    log.info("===사용자 역할(Role IDs) : {}", ud.getRoleIds());
                                } else {
                                    log.info("===사용자 Principal : {}", authItem.getPrincipal());
                                }
                            } else {
                                log.info("===사용자 상태 : 비로그인 (Anonymous)");
                            }

                            org.springframework.security.authorization.AuthorizationDecision decision =
                                dynamicAuthz.check(authSupplier, ctx);
                            log.info("===최종 승인 여부 : {}",
                                decision != null && decision.isGranted() ? "GRANTED (통과)" : "DENIED (거부)");
                            log.info("================================================================");
                            return decision;
                        }))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .formLogin(form -> form
                        .loginPage(MEMBER_LOGIN)
                        .loginProcessingUrl(MEMBER_LOGIN)
                        .usernameParameter("loginId")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, MEMBER_LOGOUT))
                        // 세션 무효화로 sticky siteCode 가 사라지므로 핸들러가 hidden 필드로 받아 URL 에 보존
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .deleteCookies(SESSION_COOKIE, CSRF_COOKIE)
                        .invalidateHttpSession(true));

        applyCommonHeaders(http);
        // adminLoginIpGateFilter 는 걸지 않는다 — 회원은 임의 망에서 접속한다
        http.addFilterBefore(suspiciousRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cspNonceFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginFormatValidationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

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
