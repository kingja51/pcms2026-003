package com.gonet.config.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI 3 설정 — Swagger UI / api-docs 노출 정책.
 *
 * <p>노출 범위 (eGov + GoPCMS 규약):
 * <ul>
 *   <li>{@code /api/v1/**} REST 엔드포인트 ({@code *ApiController}) 만 노출</li>
 *   <li>{@code /admin/**} (MngController) 와 사이트 HTML(UsrController) 은 OpenAPI 대상이 아님</li>
 * </ul>
 *
 * <p>인증 모델 — 본 시스템은 세션 쿠키 기반 (Bearer JWT 미사용). Swagger UI "Authorize" 버튼이
 * 노출되지만 직접 토큰 입력 불가 — 사용자는 {@code /member/login} / {@code /admin/login} 으로
 * 별도 로그인 후 같은 브라우저로 Swagger UI 에 진입해야 인증 쿠키({@code PCMS_SID}) 가 자동 전송됨.
 * CSRF 가 필요한 mutation 호출은 Swagger UI 의 "Try it out" 으로 직접 실행할 수 없음
 * (CSRF 토큰 발급 경로 분리됨) — 운영 트레이싱은 cURL/Postman 권장.
 *
 * <p>URL access — {@code tb_role_url_access} 에서 ROLE_ADMIN 전용
 * (DDL 요청서 {@code 2026-04-27_swagger_url_access.sql} 참조). 운영 노출 = 공격면 확대이므로
 * 회원/직원 계정에는 노출되지 않는다.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_SESSION = "SessionCookieAuth";

    @Bean
    public OpenAPI goPcmsOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("GoPCMS 2026 API")
                .description("""
                    GoPCMS — eGovFramework 5.0 호환 멀티사이트 CMS 의 REST API 문서.

                    * URL prefix: `/api/v1/**`
                    * 응답 래퍼: `ApiResponse<T>` — `{ success, message, data }`
                    * 인증: 세션 쿠키(`PCMS_SID`) — 별도 로그인 후 Swagger UI 진입 시 자동 전송
                    * 관리자 전용 노출 (`ROLE_ADMIN`) — DDL `2026-04-27_swagger_url_access.sql`

                    HTML 페이지(`UsrController`/`MngController`) 는 OpenAPI 대상이 아님.""")
                .version("1.0.0")
                .contact(new Contact()
                    .name("GoNet")
                    .email("admin@gonet.com")
                    .url("https://www.gonet.co.kr"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://www.gonet.co.kr")))
            .externalDocs(new ExternalDocumentation()
                .description("CLAUDE.md — 개발 가이드")
                .url("https://github.com/gonet/gopcms"))
            .servers(List.of(
                new Server().url("/").description("Same-origin (현재 호스트)"),
                new Server().url("http://localhost").description("로컬 개발"),
                new Server().url("https://gopcms.example.com").description("스테이징 (예시)")))
            // 세션 쿠키 기반 — Swagger UI 의 Authorize 버튼은 시각화 용도. 실 인증은 별도 로그인 페이지.
            .components(new Components()
                .addSecuritySchemes(SECURITY_SCHEME_SESSION,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("PCMS_SID")
                        .description("세션 쿠키 — `/member/login` 또는 `/admin/login` 후 자동 발급")))
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_SESSION));
    }

    /**
     * 공개 API 그룹 — anonymous 접근 가능한 엔드포인트.
     * Swagger UI 의 그룹 selector 에서 "public" 으로 노출.
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .displayName("Public — anonymous 허용")
            .pathsToMatch("/api/v1/search/suggest",
                          "/api/v1/system/**",
                          "/api/v1/weather/**")
            .build();
    }

    /**
     * 인증 사용자 API 그룹 — 로그인이 필요한 엔드포인트 (파일/게시판/알림 등).
     * 본 그룹의 모든 작업은 세션 쿠키 인증을 전제.
     */
    @Bean
    public GroupedOpenApi authenticatedApi() {
        return GroupedOpenApi.builder()
            .group("authenticated")
            .displayName("Authenticated — 로그인 필요")
            .pathsToMatch("/api/v1/file/**",
                          "/api/v1/board/**",
                          "/api/v1/notification/**")
            .build();
    }

    /**
     * 전체 API 그룹 — 모든 {@code /api/v1/**} 노출. 운영 시 단일 화면에서 일괄 조회 용.
     */
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
            .group("all")
            .displayName("All — 전체 REST")
            .pathsToMatch("/api/v1/**")
            .build();
    }
}
