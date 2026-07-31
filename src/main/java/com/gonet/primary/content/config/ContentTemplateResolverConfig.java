package com.gonet.primary.content.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import jakarta.annotation.PostConstruct;

/**
 * ContentFileWriter 가 외부 디렉터리({@code gopcms.content.sync.html-root}) 에 기록한
 * CMS 콘텐츠 HTML 파일을 Thymeleaf 가 직접 읽도록 하는 보조 TemplateResolver.
 *
 * <p>왜 필요한가:
 * <ul>
 *   <li>운영 war 배포 환경에서는 {@code src/main/resources/templates} 가 jar 내부라 쓰기 불가</li>
 *   <li>관리자가 [파일 생성] 클릭 시 외부 디렉터리(예: {@code D:/data/gopcms2026/content-templates})
 *       에 파일이 떨어져야 함</li>
 *   <li>그 외부 디렉터리도 Thymeleaf 의 view 해석 경로에 포함시켜야 사용자 페이지가
 *       즉시 그 파일을 읽어 렌더할 수 있음</li>
 * </ul>
 *
 * <p>해석 순서: 기본 classpath:/templates/ (시스템/관리자/레이아웃 등 source-controlled) →
 * 본 resolver 의 외부 디렉터리 (CMS 가 생성한 콘텐츠). 동일 경로가 양쪽에 있으면 order 가
 * 낮은 classpath 우선 — 시스템 템플릿이 CMS 가 만든 파일로 덮어쓰이지 않도록 보호.
 *
 * <p>{@code gopcms.content.sync.html-root} 미설정 시 본 빈 미등록 — 기존 classpath
 * 해석만 동작 (로컬 IDE 등 외부 디렉터리 불필요 환경).
 */
@Configuration
@ConditionalOnProperty(prefix = "gopcms.content.sync", name = "html-root")
public class ContentTemplateResolverConfig {

    @Value("${gopcms.content.sync.html-root}")
    private String htmlRoot;

    private final SpringTemplateEngine templateEngine;

    public ContentTemplateResolverConfig(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @PostConstruct
    void registerExternalResolver() {
        FileTemplateResolver r = new FileTemplateResolver();
        // 끝 슬래시 보장 — Thymeleaf 가 prefix + viewName + suffix 로 경로 조합
        String prefix = htmlRoot.endsWith("/") ? htmlRoot : htmlRoot + "/";
        r.setPrefix(prefix);
        r.setSuffix(".html");
        r.setTemplateMode(TemplateMode.HTML);
        r.setCharacterEncoding("UTF-8");
        r.setCacheable(false);                // CMS 가 갱신한 파일 즉시 반영
        r.setCheckExistence(true);            // 파일 없으면 다음 resolver 로
        r.setOrder(50);                       // SpringResourceTemplateResolver 보통 1 → classpath 우선
        templateEngine.addTemplateResolver(r);
    }
}
