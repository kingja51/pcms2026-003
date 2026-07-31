package com.gonet.primary.content.service;

import com.gonet.primary.content.dto.Content;
import com.gonet.primary.content.mapper.ContentMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gonet.config.datasource.PrimaryDataSourceConfig;

/**
 * 디스크의 실제 HTML 파일을 truth source 로 보고
 * tb_content 의 body / original_content / summary 를 동기화.
 *
 * <p>경로 규약: {@code /templates/site/{site_code}/{slug}.html}
 * <ul>
 *   <li>1순위 — classpath: {@code templates/site/{siteCode}/{slug}.html} (jar 내부 / IDE classpath)</li>
 *   <li>2순위 — file: {@code <project>/src/main/resources/templates/site/{siteCode}/{slug}.html}
 *       (개발 시 핫리로드 대비). {@code gopcms.content.sync.html-root} 로 override 가능</li>
 * </ul>
 *
 * <p>추출 규칙:
 * <ul>
 *   <li>{@code <... layout:fragment="content" ...>} 블록 우선 — Thymeleaf 레이아웃 fragment 안만</li>
 *   <li>없으면 {@code <body>...</body>} — 외부 자산/메타 제외</li>
 *   <li>둘 다 없으면 raw 전체</li>
 * </ul>
 *
 * <p>{@code body} = 추출한 HTML 그대로,
 * {@code originalContent} = 자체 minimal HTML→Markdown 변환 (h1~h6/p/li/a/img/code/pre/strong/em/br/hr),
 * {@code summary} = strip text 의 앞 200자.
 */
@Service
public class ContentHtmlSyncer extends EgovAbstractServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(ContentHtmlSyncer.class);

    private static final PolicyFactory STRIP_POLICY = new HtmlPolicyBuilder().toFactory();
    private static final int SUMMARY_MAX_LEN = 200;

    /** {@code layout:fragment="content"} 블록 추출 — 첫 매치, non-greedy. */
    private static final Pattern LAYOUT_FRAGMENT = Pattern.compile(
        "<(\\w+)\\b[^>]*layout:fragment\\s*=\\s*\"content\"[^>]*>([\\s\\S]*?)</\\1>",
        Pattern.CASE_INSENSITIVE);

    /** {@code <body>...</body>} 추출 — 첫 매치. */
    private static final Pattern BODY_TAG = Pattern.compile(
        "<body\\b[^>]*>([\\s\\S]*?)</body>", Pattern.CASE_INSENSITIVE);

    private final ContentMapper contentMapper;

    /** 운영 배포 시 외부 templates 디렉터리를 가리키는 override 경로. */
    @Value("${gopcms.content.sync.html-root:}")
    private String htmlRootOverride;

    public ContentHtmlSyncer(ContentMapper contentMapper) {
        this.contentMapper = contentMapper;
    }

    /** 동기화 결과 — UI 통계 분류용. */
    public enum SyncResult {
        /** 디스크 HTML 읽기 + DB UPDATE 성공. */
        UPDATED,
        /** 디스크 hash 가 DB body_hash 와 동일 — UPDATE skip. */
        UNCHANGED,
        /** 디스크 파일 없음. */
        MISSING,
        /** 읽기/UPDATE 실패. */
        FAILED
    }

    /**
     * 단건 동기화. 디스크 hash 비교 후 변경된 경우만 UPDATE.
     *
     * <p>- UPDATED → DB 본문/hash 갱신 + Content DTO 도 새 값으로 동기화
     * <p>- UNCHANGED → no-op (이미 DTO 의 body/original/summary 가 최신)
     * <p>- MISSING/FAILED → 색인은 DTO 의 기존 DB 본문으로 진행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public SyncResult syncFromDisk(Content c) {
        if (c == null || c.getContentId() == null) return SyncResult.FAILED;
        if (c.getSiteCode() == null || c.getSiteCode().isBlank()) return SyncResult.MISSING;
        if (c.getSlug() == null || c.getSlug().isBlank()) return SyncResult.MISSING;

        String html;
        try {
            html = readHtml(c.getSiteCode().trim(), c.getSlug().trim());
        } catch (IOException ex) {
            log.warn("CONTENT_SYNC_READ_FAIL contentId={} site={} slug={} reason={}",
                c.getContentId(), c.getSiteCode(), c.getSlug(), ex.getMessage());
            return SyncResult.FAILED;
        }
        if (html == null) {
            log.info("===CONTENT_SYNC_SKIP contentId={} site={} slug={} reason=file-not-found",
                c.getContentId(), c.getSiteCode(), c.getSlug());
            return SyncResult.MISSING;
        }

        // 1) 디스크 원문 hash 계산 후 DB 의 body_hash 와 비교 — 같으면 skip
        String diskHash = sha256Hex(html);
        if (diskHash != null && diskHash.equalsIgnoreCase(c.getBodyHash())) {
            log.info("CONTENT_SYNC_UNCHANGED contentId={} hash={}",
                c.getContentId(), diskHash);
            return SyncResult.UNCHANGED;
        }

        // 2) 변경됨 — body/original/summary 추출 후 DB UPDATE
        String body     = extractContentBlock(html);
        String markdown = htmlToMarkdown(body);
        String text     = stripToText(body);
        String summary  = text.length() > SUMMARY_MAX_LEN
            ? text.substring(0, SUMMARY_MAX_LEN) : text;

        try {
            int n = contentMapper.updateBodyOriginalSummary(
                c.getContentId(), body, markdown, summary, diskHash);
            if (n > 0) {
                c.setBody(body);
                c.setOriginalContent(markdown);
                c.setSummary(summary);
                c.setBodyHash(diskHash);
                log.info("===CONTENT_SYNC_OK contentId={} site={} slug={} bodyLen={} mdLen={} sumLen={} hash={}",
                    c.getContentId(), c.getSiteCode(), c.getSlug(),
                    body.length(), markdown.length(), summary.length(),
                    diskHash == null ? "" : diskHash.substring(0, 12));
                return SyncResult.UPDATED;
            }
            return SyncResult.FAILED;
        } catch (Exception ex) {
            log.warn("CONTENT_SYNC_UPDATE_FAIL contentId={} reason={}",
                c.getContentId(), ex.getMessage());
            return SyncResult.FAILED;
        }
    }

    /** SHA-256 hex. */
    static String sha256Hex(String s) {
        if (s == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    /** classpath → 파일시스템 순으로 시도. 둘 다 실패면 null. */
    private String readHtml(String siteCode, String slug) throws IOException {
        String relative = "templates/site/" + siteCode + "/" + slug + ".html";

        // 1) classpath
        Resource cp = new ClassPathResource(relative);
        if (cp.exists()) {
            try (var in = cp.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        // 2) 외부 디렉터리 (override) 또는 dev resources
        Path[] candidates = htmlRootOverride != null && !htmlRootOverride.isBlank()
            ? new Path[]{ Paths.get(htmlRootOverride, "site", siteCode, slug + ".html") }
            : new Path[]{
                Paths.get("src", "main", "resources", "templates", "site", siteCode, slug + ".html"),
                Paths.get("templates", "site", siteCode, slug + ".html")
            };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return Files.readString(p, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** layout:fragment="content" → body → raw 순으로 본문 영역 추출. */
    static String extractContentBlock(String html) {
        if (html == null) return "";
        Matcher mf = LAYOUT_FRAGMENT.matcher(html);
        if (mf.find()) return mf.group(2).trim();
        Matcher mb = BODY_TAG.matcher(html);
        if (mb.find()) return mb.group(1).trim();
        return html.trim();
    }

    /** OWASP sanitizer 로 모든 태그 제거 + 엔티티 unescape + 공백 압축. */
    static String stripToText(String html) {
        if (html == null || html.isBlank()) return "";
        String s = STRIP_POLICY.sanitize(html);
        s = s.replace("&nbsp;", " ")
             .replace("&amp;", "&")
             .replace("&lt;", "<")
             .replace("&gt;", ">")
             .replace("&quot;", "\"")
             .replace("&#39;", "'");
        return s.replaceAll("\\s+", " ").trim();
    }

    // ────────────────────────────────────────────────────────────────
    // minimal HTML → Markdown 변환
    // ────────────────────────────────────────────────────────────────
    // 외부 라이브러리 의존 없이 정규식 기반 — 한계: 중첩 표/리스트, 인라인 style/class
    // 무시. 보존 우선이 아닌 "검색용 텍스트" 가 목적이므로 완벽 변환은 불요.

    static String htmlToMarkdown(String html) {
        if (html == null || html.isBlank()) return "";
        String s = html;

        // 0) 주석 / script / style 통째로 제거
        s = s.replaceAll("(?is)<!--.*?-->", "");
        s = s.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "");
        s = s.replaceAll("(?is)<style\\b[^>]*>.*?</style>", "");

        // 1) 헤딩
        s = s.replaceAll("(?is)<h1\\b[^>]*>(.*?)</h1>", "\n\n# $1\n\n");
        s = s.replaceAll("(?is)<h2\\b[^>]*>(.*?)</h2>", "\n\n## $1\n\n");
        s = s.replaceAll("(?is)<h3\\b[^>]*>(.*?)</h3>", "\n\n### $1\n\n");
        s = s.replaceAll("(?is)<h4\\b[^>]*>(.*?)</h4>", "\n\n#### $1\n\n");
        s = s.replaceAll("(?is)<h5\\b[^>]*>(.*?)</h5>", "\n\n##### $1\n\n");
        s = s.replaceAll("(?is)<h6\\b[^>]*>(.*?)</h6>", "\n\n###### $1\n\n");

        // 2) 코드 블록 / 인라인 코드
        s = s.replaceAll("(?is)<pre\\b[^>]*>\\s*<code\\b[^>]*>(.*?)</code>\\s*</pre>",
            "\n\n```\n$1\n```\n\n");
        s = s.replaceAll("(?is)<pre\\b[^>]*>(.*?)</pre>", "\n\n```\n$1\n```\n\n");
        s = s.replaceAll("(?is)<code\\b[^>]*>(.*?)</code>", "`$1`");

        // 3) 강조
        s = s.replaceAll("(?is)<(?:strong|b)\\b[^>]*>(.*?)</(?:strong|b)>", "**$1**");
        s = s.replaceAll("(?is)<(?:em|i)\\b[^>]*>(.*?)</(?:em|i)>", "*$1*");

        // 4) 링크 / 이미지
        s = s.replaceAll("(?is)<img\\b[^>]*\\balt\\s*=\\s*\"([^\"]*)\"[^>]*\\bsrc\\s*=\\s*\"([^\"]*)\"[^>]*/?>",
            "![$1]($2)");
        s = s.replaceAll("(?is)<img\\b[^>]*\\bsrc\\s*=\\s*\"([^\"]*)\"[^>]*\\balt\\s*=\\s*\"([^\"]*)\"[^>]*/?>",
            "![$2]($1)");
        s = s.replaceAll("(?is)<img\\b[^>]*\\bsrc\\s*=\\s*\"([^\"]*)\"[^>]*/?>", "![]($1)");
        s = s.replaceAll("(?is)<a\\b[^>]*\\bhref\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</a>", "[$2]($1)");

        // 5) 리스트 — li 만 - 로, ul/ol 태그는 제거 (중첩은 들여쓰기 보장 안 됨)
        s = s.replaceAll("(?is)<li\\b[^>]*>(.*?)</li>", "- $1\n");
        s = s.replaceAll("(?is)</?(?:ul|ol)\\b[^>]*>", "\n");

        // 6) blockquote
        s = s.replaceAll("(?is)<blockquote\\b[^>]*>(.*?)</blockquote>", "\n> $1\n");

        // 7) 줄바꿈 / 단락 / hr
        s = s.replaceAll("(?is)<br\\s*/?>", "\n");
        s = s.replaceAll("(?is)<hr\\s*/?>", "\n\n---\n\n");
        s = s.replaceAll("(?is)<p\\b[^>]*>(.*?)</p>", "\n\n$1\n\n");

        // 8) 그 외 모든 태그 제거 (속성 포함)
        s = s.replaceAll("(?is)<[^>]+>", "");

        // 9) HTML 엔티티
        s = s.replace("&nbsp;", " ")
             .replace("&amp;", "&")
             .replace("&lt;", "<")
             .replace("&gt;", ">")
             .replace("&quot;", "\"")
             .replace("&#39;", "'");

        // 10) 공백 정규화 — 연속 빈 줄 압축, trailing 공백 제거
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll(" *\\n", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }
}
