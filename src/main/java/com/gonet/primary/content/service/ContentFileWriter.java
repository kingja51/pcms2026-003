package com.gonet.primary.content.service;

import com.gonet.primary.content.dto.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Content 의 body / title 을 실제 Thymeleaf 템플릿 파일로 디스크에 기록.
 *
 * <p>경로 규약: {@code <root>/site/{siteCode}/{slug}.html}
 * <ul>
 *   <li>root 기본값 — {@code src/main/resources/templates} (개발 환경 dev resources)</li>
 *   <li>운영 override — {@code gopcms.content.sync.html-root} 설정값</li>
 * </ul>
 *
 * <p>경로 traversal 방어:
 * <ul>
 *   <li>siteCode / slug 모두 정규식 화이트리스트 검증 ({@link #SAFE_TOKEN})</li>
 *   <li>해석된 절대경로가 root 절대경로 아래에 위치하는지 재확인 (target/backup 모두)</li>
 * </ul>
 *
 * <p>덮어쓰기 정책 — 동일 경로에 파일이 이미 있으면, 덮어쓰기 직전에
 * {@code <root>/site/{siteCode}/backup/{slug}_yyyyMMddHHmm.html} 로 복사 후 원본을 새 내용으로 교체.
 * 백업 실패 시 IOException 으로 전파하여 원본을 보존.
 *
 * <p>{@link ContentHtmlSyncer} 는 본 writer 가 작성한 파일을 truth source 로 동작 — 작성 후
 * SHA-256 hash 가 달라지면 다음 sync 사이클에서 DB body/original/summary 를 갱신.
 */
@Service
public class ContentFileWriter {

    private static final Logger log = LoggerFactory.getLogger(ContentFileWriter.class);

    /** siteCode / slug 공통 — 영숫자 + 하이픈/언더스코어, 1~64자. 점/슬래시 차단. */
    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** 백업 파일명 타임스탬프 — yyyyMMddHHmm (분 단위까지). */
    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 백업 디렉터리 이름 — siteCode 디렉터리 안에 둔다. */
    private static final String BACKUP_DIR = "backup";

    /** Thymeleaf 템플릿 본체. {0} = title, {1} = body. */
    private static final String TEMPLATE = """
        <!DOCTYPE html>
        <html xmlns:th="http://www.thymeleaf.org"
              xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
              layout:decorate="~{__${layoutPath}__}"
              lang="ko">
        <head><title>{0}</title></head>
        <body>
        <section layout:fragment="content">

         {1}

        </section>
        </body>
        </html>
        """;

    @Value("${gopcms.content.sync.html-root:}")
    private String htmlRootOverride;

    /**
     * Content 의 title/body 를 템플릿에 주입한 뒤 디스크에 atomic 기록.
     *
     * @return 기록된 절대경로
     * @throws IllegalArgumentException siteCode/slug 검증 실패 또는 PUBLISHED 가 아닐 때
     * @throws IOException              파일 쓰기 실패
     */
    public Path write(Content c) throws IOException {
        if (c == null) {
            throw new IllegalArgumentException("content is null");
        }
        String siteCode = trim(c.getSiteCode());
        String slug     = trim(c.getSlug());

        if (siteCode == null || !SAFE_TOKEN.matcher(siteCode).matches()) {
            throw new IllegalArgumentException("siteCode 가 비었거나 유효하지 않습니다: " + c.getSiteCode());
        }
        if (slug == null || !SAFE_TOKEN.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug 가 비었거나 유효하지 않습니다: " + c.getSlug());
        }

        Path root   = resolveRoot();
        Path siteDir = root.resolve("site").resolve(siteCode).normalize().toAbsolutePath();
        Path target  = siteDir.resolve(slug + ".html").normalize().toAbsolutePath();

        // path-traversal 재확인 — 정규식을 통과해도 정규화 후 root 안에 있는지 명시 검증
        Path absRoot = root.toAbsolutePath().normalize();
        if (!target.startsWith(absRoot)) {
            throw new IllegalArgumentException("경로 위반 — 허용 root 밖: " + target);
        }

        Files.createDirectories(siteDir);

        // 기존 파일이 있으면 backup/{slug}_yyyyMMddHHmm.html 로 복사 (덮어쓰기 전)
        if (Files.isRegularFile(target)) {
            Path backupDir = siteDir.resolve(BACKUP_DIR).normalize().toAbsolutePath();
            if (!backupDir.startsWith(absRoot)) {
                throw new IllegalArgumentException("경로 위반 — 백업 root 밖: " + backupDir);
            }
            Files.createDirectories(backupDir);
            String stamp      = LocalDateTime.now().format(BACKUP_TS);
            Path   backupFile = backupDir.resolve(slug + "_" + stamp + ".html");
            try {
                Files.copy(target, backupFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("===CONTENT_FILE_BACKUP ok contentId={} from={} to={}",
                    c.getContentId(), target, backupFile);
            } catch (IOException ex) {
                log.warn("CONTENT_FILE_BACKUP fail contentId={} from={} reason={}",
                    c.getContentId(), target, ex.getMessage());
                throw ex;
            }
        }

        String title = c.getTitle() == null ? "" : c.getTitle();
        String body  = c.getBody()  == null ? "" : c.getBody();
        String html  = TEMPLATE
            .replace("{0}", htmlEscape(title))
            .replace("{1}", body);

        // atomic write — 임시 파일 작성 후 rename
        Path tmp = siteDir.resolve(slug + ".html.tmp");
        Files.writeString(tmp, html, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        try {
            Files.move(tmp, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            // 일부 파일시스템(Windows 위 SMB 등)은 ATOMIC 미지원 — 비-atomic 폴백
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("===CONTENT_FILE_WRITE ok contentId={} site={} slug={} path={} bytes={}",
            c.getContentId(), siteCode, slug, target, html.length());
        return target;
    }

    /** root 디렉터리 결정 — override 우선, 아니면 dev resources. */
    private Path resolveRoot() {
        if (htmlRootOverride != null && !htmlRootOverride.isBlank()) {
            return Paths.get(htmlRootOverride);
        }
        return Paths.get("src", "main", "resources", "templates");
    }

    private static String trim(String s) {
        return (s == null) ? null : s.trim();
    }

    /** 최소 HTML 이스케이프 — title 태그 안 안전화 용도. */
    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
