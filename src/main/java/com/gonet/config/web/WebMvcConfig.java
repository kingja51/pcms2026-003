package com.gonet.config.web;

import com.gonet.config.interceptor.AuditHttpInterceptor;
import com.gonet.config.interceptor.TwoFactorEnforcementInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

import java.time.Duration;

/**
 * Spring MVC 커스텀 설정 — Interceptor + 정적 자원 캐시 헤더.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuditHttpInterceptor auditHttpInterceptor;
    private final TwoFactorEnforcementInterceptor twoFactorEnforcementInterceptor;
    private final Environment environment;

    public WebMvcConfig(AuditHttpInterceptor auditHttpInterceptor,
                        TwoFactorEnforcementInterceptor twoFactorEnforcementInterceptor,
                        Environment environment) {
        this.auditHttpInterceptor = auditHttpInterceptor;
        this.twoFactorEnforcementInterceptor = twoFactorEnforcementInterceptor;
        this.environment = environment;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 2FA 관문 — 관리자 영역 진입 시 세션 플래그 검증 (셀프 등록 예외) [P2에서 복원됨]
        registry.addInterceptor(twoFactorEnforcementInterceptor)
            .addPathPatterns("/admin/**")
            .excludePathPatterns("/admin/me/2fa/**");

        // 감사 자동 포착 — /admin/** CUD 만 (로그인은 Login{Success,Failure}Handler 가 처리)
        registry.addInterceptor(auditHttpInterceptor)
            .addPathPatterns("/admin/**");
    }

    /**
     * H-7 — 정적 자원 Cache-Control 헤더 부착.
     *
     * <p>기본 Spring Boot 는 정적 자원에 캐시 헤더를 붙이지 않아 브라우저가 매 요청마다
     * If-Modified-Since 라운드트립을 발생시킨다. 1년 max-age + content-hash 기반 fingerprint
     * 로 변경된 자원만 재다운로드되도록 설정.
     *
     * <p>변경된 파일은 ContentVersionStrategy 의 SHA-1 해시가 URL 에 자동 부착되어
     * 캐시 무효화. Thymeleaf 의 {@code @{/static/...}} 사용 시 자동 적용.
     *
     * <p><b>local 프로파일 예외</b> — 개발 중 CSS/JS 반복 수정 시 immutable 캐시 때문에
     * 하드리프레시(Ctrl+Shift+R)를 매번 눌러야 하는 불편을 없앤다. local 에서는
     * {@code no-store} 로 캐시를 끄고 content-hash fingerprint(VersionResourceResolver)도
     * 생략 → 저장·재빌드 후 새로고침만으로 즉시 반영. 운영(dev/staging/prod)은 기존 1년
     * immutable + fingerprint 그대로.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        boolean isLocal = environment.acceptsProfiles("local");

        var registration = registry.addResourceHandler("/static/**", "/css/**", "/js/**",
                                     "/img/**", "/fonts/**", "/tmpl/**")
            .addResourceLocations("classpath:/static/",
                                  "classpath:/static/css/",
                                  "classpath:/static/js/",
                                  "classpath:/static/img/",
                                  "classpath:/static/fonts/",
                                  "classpath:/static/tmpl/");

        if (isLocal) {
            // 개발 편의 — 캐시 무효화. 매 요청 최신 파일 서빙 (하드리프레시 불필요).
            registration.setCacheControl(CacheControl.noStore());
            return;
        }

        // 운영 — 1년 immutable + content-hash fingerprint 로 캐시 무효화.
        CacheControl yearImmutable = CacheControl.maxAge(Duration.ofDays(365))
            .cachePublic()
            .immutable();
        registration
            .setCacheControl(yearImmutable)
            .resourceChain(true)
            .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));
    }
}
