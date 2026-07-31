package com.gonet.primary.system.site.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 사이트 콘텐츠 템플릿 스캐폴더 — site_code 는 있는데 콘텐츠 폴더가 없는 사이트에
 * 샘플 서식({@code templates/samples/*.html})을 복사해 시작점을 만들어 준다.
 *
 * <p>복사 대상은 외부 콘텐츠 루트 {@code {gopcms.content.sync.html-root}/site/{siteCode}/} —
 * FileTemplateResolver(ContentTemplateResolverConfig, cache off)가 즉시 뷰로 해석하므로
 * 재빌드 없이 바로 렌더된다. 각 학과는 복사된 파일을 자기 내용으로 수정한다(서식→커스텀).
 *
 * <p>규칙:
 * <ul>
 *   <li>classpath {@code templates/site/{siteCode}/home.html} 이 있는 사이트(me/cse/ibm 등
 *       소스 관리 사이트)는 건너뜀 — classpath 가 항상 우선이라 복사 무의미.</li>
 *   <li>이미 존재하는 파일은 덮어쓰지 않음(학과 수정본 보호). 파일 단위 멱등.</li>
 *   <li>html-root 미설정(운영 외 환경 등) 시 no-op — 호출측은 generic-* 공용 뷰로 폴백.</li>
 * </ul>
 *
 * <p>호출: {@code DefaultUsrController}(요청 시 lazy) + 향후 사이트 빌더(SiteBuilderService)의
 * 생성 단계에서 재사용(설계서 '건양대학교 학과 홈페이지 빌더 설계.md' §6).
 */
@Component
public class SiteTemplateScaffolder {

    private static final Logger log = LoggerFactory.getLogger(SiteTemplateScaffolder.class);

    /** 복사할 샘플 서식 slug — templates/samples/{slug}.html. */
    public static final List<String> SAMPLE_SLUGS = List.of("home", "about", "location");

    /** site_code 안전 문자(경로 조작 차단) — tb_site.site_code varchar(30). */
    private static final Pattern SAFE_CODE = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,29}$");

    /** ContentFileWriter / ContentTemplateResolverConfig 와 동일 경로. 미설정 시 no-op. */
    @Value("${gopcms.content.sync.html-root:}")
    private String htmlRoot;

    /**
     * {@code {html-root}/site/{siteCode}/} 에 샘플 서식이 없으면 복사. 파일 단위 멱등 —
     * 이미 있는 파일은 보존. 실패는 warn 로그 후 무시(페이지는 generic 뷰로 폴백 가능).
     *
     * @return 하나 이상 복사했으면 true
     */
    public boolean ensureScaffold(String siteCode) {
        if (htmlRoot == null || htmlRoot.isBlank()) return false;
        if (siteCode == null || !SAFE_CODE.matcher(siteCode).matches()) return false;
        // 소스 관리 사이트(classpath 콘텐츠 보유)는 스캐폴드 불필요 — classpath 우선 해석
        if (new ClassPathResource("templates/site/" + siteCode + "/home.html").exists()) return false;

        boolean copied = false;
        try {
            Path dir = Paths.get(htmlRoot, "site", siteCode);
            if (!Files.isDirectory(dir)) Files.createDirectories(dir);
            for (String slug : SAMPLE_SLUGS) {
                Path target = dir.resolve(slug + ".html");
                if (Files.exists(target)) continue;               // 학과 수정본/기존 파일 보존
                ClassPathResource sample = new ClassPathResource("templates/samples/" + slug + ".html");
                if (!sample.exists()) {
                    log.warn("SITE_SCAFFOLD_SAMPLE_MISSING slug={}", slug);
                    continue;
                }
                try (InputStream in = sample.getInputStream()) {
                    Files.copy(in, target);
                    copied = true;
                }
            }
            if (copied) log.info("SITE_SCAFFOLD siteCode={} dir={}", siteCode, dir);
        } catch (Exception ex) {
            log.warn("SITE_SCAFFOLD_FAIL siteCode={} reason={}", siteCode, ex.getMessage());
        }
        return copied;
    }
}
