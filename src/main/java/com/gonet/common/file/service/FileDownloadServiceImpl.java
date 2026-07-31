package com.gonet.common.file.service;

import com.gonet.common.file.dto.ZipEntrySpec;
import com.gonet.common.file.security.FileStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@link FileDownloadService} 구현 — Range/ETag/Conditional GET/HEAD 전담.
 *
 * <p>지원 조건부 헤더:
 * <ul>
 *   <li>{@code If-None-Match} → 304 Not Modified (ETag 일치)</li>
 *   <li>{@code If-Modified-Since} → 304 Not Modified (Last-Modified 동일/이전)</li>
 *   <li>{@code If-Match} → 412 Precondition Failed (ETag 불일치)</li>
 *   <li>{@code If-Range} → Range 요청과 결합 시, ETag 또는 Last-Modified 일치하면 206,
 *       불일치하면 200 전체 응답으로 강등 (RFC 7233 §3.2)</li>
 * </ul>
 *
 * <p>HEAD 메서드는 동일 헤더를 세팅하되 본문 전송을 생략 (Spring 기본 wrapping 으로 자동
 * 처리되므로 본 구현이 직접 분기하지 않는다 — 단, content body skip 명시).
 */
@Service
public class FileDownloadServiceImpl implements FileDownloadService {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadServiceImpl.class);

    private static final int TRANSFER_BUFFER = 64 * 1024;

    private final FileStorage storage;

    public FileDownloadServiceImpl(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public long download(String relative, String originalName, String mime, String fileHash,
                          HttpServletRequest req, HttpServletResponse res) {
        return download(relative, originalName, mime, fileHash, req, res, /*inline=*/ false);
    }

    @Override
    public long download(String relative, String originalName, String mime, String fileHash,
                          HttpServletRequest req, HttpServletResponse res,
                          boolean inline) {
        Path path = storage.resolveStoredPath(relative);
        if (!Files.isRegularFile(path)) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }

        long total;
        long lastModifiedMillis;
        try {
            total = Files.size(path);
            FileTime mt = Files.getLastModifiedTime(path);
            // HTTP-date 는 초 정밀도까지 — 밀리초 절삭으로 If-Modified-Since 비교 안정화
            lastModifiedMillis = (mt.toMillis() / 1000L) * 1000L;
        } catch (IOException ex) {
            log.warn("DOWNLOAD stat failed path={} reason={}", path, ex.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return 0;
        }

        String etag = "\"" + (fileHash == null ? "" : fileHash) + "\"";
        String lastModifiedHttp = formatHttpDate(lastModifiedMillis);

        // ── 조건부 헤더 (RFC 7232) — 우선순위: If-Match → If-None-Match → If-Modified-Since ──

        // If-Match: 클라이언트가 특정 버전 요구 — 일치하지 않으면 412
        String ifMatch = req.getHeader("If-Match");
        if (ifMatch != null && !ifMatch.isBlank()
            && !"*".equals(ifMatch.trim())
            && !etagMatches(ifMatch, etag)) {
            res.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            res.setHeader("ETag", etag);
            return 0;
        }

        // If-None-Match: ETag 가 동일하면 304 (강 검증자 — 가장 우선)
        String ifNoneMatch = req.getHeader("If-None-Match");
        if (ifNoneMatch != null && etagMatches(ifNoneMatch, etag)) {
            writeNotModifiedHeaders(res, etag, lastModifiedHttp);
            return 0;
        }

        // If-Modified-Since: 약 검증자 — If-None-Match 가 없을 때만 평가 (RFC 7232 §3.3)
        if (ifNoneMatch == null) {
            long ims = parseHttpDate(req.getHeader("If-Modified-Since"));
            if (ims > 0 && lastModifiedMillis <= ims) {
                writeNotModifiedHeaders(res, etag, lastModifiedHttp);
                return 0;
            }
        }

        // ── Range + If-Range ──
        long[] range = parseRange(req.getHeader("Range"), total);

        // If-Range: validator 일치 시만 Range 유지, 불일치 시 200 전체로 강등 (RFC 7233 §3.2)
        if (range != null) {
            String ifRange = req.getHeader("If-Range");
            if (ifRange != null && !ifRange.isBlank() && !ifRangeMatches(ifRange, etag, lastModifiedMillis)) {
                range = null;
            }
        }

        // 공통 헤더
        res.setHeader("ETag", etag);
        res.setHeader("Last-Modified", lastModifiedHttp);
        res.setHeader("Accept-Ranges", "bytes");
        res.setHeader("Cache-Control", "private, no-store");
        res.setContentType(mime != null && !mime.isBlank() ? mime : "application/octet-stream");
        res.setHeader("Content-Disposition", contentDisposition(originalName, inline));
        res.setHeader("X-Content-Type-Options", "nosniff");

        long start;
        long end;
        if (range == null) {
            start = 0;
            end = total - 1;
            res.setStatus(HttpServletResponse.SC_OK);
        } else {
            start = range[0];
            end = range[1];
            if (start >= total || end < start) {
                res.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                res.setHeader("Content-Range", "bytes */" + total);
                return 0;
            }
            if (end >= total) end = total - 1;
            res.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            res.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + total);
        }
        long length = end - start + 1;
        res.setContentLengthLong(length);

        // HEAD 는 헤더만 보내고 본문 skip — Spring 의 HttpHeadResponseDecorator 가 이미
        // body write 를 무시하므로 명시 분기 불필요. 단, 우리 코드에서 명시적으로 short-circuit
        // 하면 zero-copy 채널을 열지 않아 효율적.
        if ("HEAD".equalsIgnoreCase(req.getMethod())) {
            return 0;
        }

        // 전송 (zero-copy FileChannel)
        long sent = 0L;
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ);
             OutputStream out = res.getOutputStream()) {
            ch.position(start);
            ByteBuffer buf = ByteBuffer.allocate(TRANSFER_BUFFER);
            long remaining = length;
            while (remaining > 0) {
                buf.clear();
                if (remaining < buf.capacity()) buf.limit((int) remaining);
                int read = ch.read(buf);
                if (read <= 0) break;
                out.write(buf.array(), 0, read);
                remaining -= read;
                sent += read;
            }
            out.flush();
        } catch (NoSuchFileException ex) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException ex) {
            // 클라이언트 연결 종료 등 — 디버그로 낮춤
            log.info("DOWNLOAD transfer aborted path={} reason={}", path, ex.getMessage());
        }
        return sent;
    }

    private static void writeNotModifiedHeaders(HttpServletResponse res, String etag, String lastModifiedHttp) {
        res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        res.setHeader("ETag", etag);
        res.setHeader("Last-Modified", lastModifiedHttp);
        res.setHeader("Cache-Control", "private, no-store");
    }

    @Override
    public long downloadZip(List<ZipEntrySpec> entries, String archiveName,
                             HttpServletRequest req, HttpServletResponse res) {
        if (entries == null || entries.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }

        String name = (archiveName == null || archiveName.isBlank()) ? "files" : archiveName;
        String archiveFilename = name + ".zip";

        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("application/zip");
        res.setHeader("Content-Disposition", contentDisposition(archiveFilename));
        res.setHeader("Accept-Ranges", "none"); // 동적 생성 — Range 미지원
        res.setHeader("X-Content-Type-Options", "nosniff");

        // 동일 entryName 중복 시 "file (2).ext" 접미사 부여
        Map<String, Integer> seen = new HashMap<>();
        long total = 0L;
        byte[] buf = new byte[TRANSFER_BUFFER];

        try (OutputStream out = res.getOutputStream();
             ZipOutputStream zout = new ZipOutputStream(out, StandardCharsets.UTF_8)) {

            for (ZipEntrySpec spec : entries) {
                Path path = storage.resolveStoredPath(spec.storedPath());
                if (!Files.isRegularFile(path)) {
                    log.warn("DOWNLOAD_ZIP skip missing path={} entry={}", path, spec.entryName());
                    continue;
                }
                String entryName = resolveUniqueName(spec.entryName(), seen);
                try (InputStream in = Files.newInputStream(path)) {
                    zout.putNextEntry(new ZipEntry(entryName));
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        zout.write(buf, 0, r);
                        total += r;
                    }
                    zout.closeEntry();
                } catch (IOException ex) {
                    log.warn("DOWNLOAD_ZIP entry failed path={} reason={}", path, ex.getMessage());
                    // 현재 entry 취소 — 다음 계속
                    try { zout.closeEntry(); } catch (IOException ignored) {}
                }
            }
            zout.finish();
            zout.flush();
        } catch (IOException ex) {
            log.info("DOWNLOAD_ZIP aborted reason={}", ex.getMessage());
        }
        return total;
    }

    @Override
    public long downloadThumbnail(String thumbnailRelative, HttpServletResponse res) {
        if (thumbnailRelative == null || thumbnailRelative.isBlank()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }
        Path path = storage.resolveThumbnailPath(thumbnailRelative);
        if (!Files.isRegularFile(path)) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }
        long total;
        try {
            total = Files.size(path);
        } catch (IOException ex) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return 0;
        }
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("image/jpeg");
        res.setHeader("Cache-Control", "private, max-age=3600");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setContentLengthLong(total);

        long sent = 0L;
        try (InputStream in = Files.newInputStream(path);
             OutputStream out = res.getOutputStream()) {
            byte[] buf = new byte[TRANSFER_BUFFER];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
                sent += r;
            }
            out.flush();
        } catch (IOException ex) {
            log.info("THUMBNAIL_DOWNLOAD aborted path={} reason={}", path, ex.getMessage());
        }
        return sent;
    }

    /**
     * 중복 entryName 에 {@code " (N)"} 접미사 부여. 확장자는 유지.
     * {@code "foo.txt"} 가 2번째면 {@code "foo (2).txt"}.
     */
    private static String resolveUniqueName(String raw, Map<String, Integer> seen) {
        String safe = (raw == null || raw.isBlank()) ? "file" : raw;
        int count = seen.merge(safe, 1, Integer::sum);
        if (count == 1) return safe;
        int dot = safe.lastIndexOf('.');
        return (dot > 0)
            ? safe.substring(0, dot) + " (" + count + ")" + safe.substring(dot)
            : safe + " (" + count + ")";
    }

    /**
     * If-Match / If-None-Match 헤더와 ETag 비교.
     *
     * <p>{@code *} 는 항상 매치(존재 의미). 콤마 구분 다중 ETag 지원. {@code W/} weak 프리픽스
     * 는 약 비교로 동등 처리(GET/HEAD 안전 메서드는 약 비교 허용 — RFC 7232 §2.3.2).
     */
    private static boolean etagMatches(String headerValue, String resourceEtag) {
        if (headerValue == null || resourceEtag == null) return false;
        String trimmed = headerValue.trim();
        if (trimmed.isEmpty()) return false;
        if ("*".equals(trimmed)) return true;
        String resourceCanonical = stripWeak(resourceEtag);
        for (String part : trimmed.split(",")) {
            String p = stripWeak(part.trim());
            if (!p.isEmpty() && p.equals(resourceCanonical)) return true;
        }
        return false;
    }

    private static String stripWeak(String etag) {
        if (etag == null) return "";
        String e = etag;
        if (e.startsWith("W/")) e = e.substring(2);
        return e;
    }

    /**
     * If-Range: ETag(완전 일치) 또는 HTTP-date(=Last-Modified) 일치 시 true.
     * 일치하지 않으면 호출자가 Range 를 무시하고 200 전체 응답으로 강등.
     */
    private static boolean ifRangeMatches(String ifRange, String etag, long lastModifiedMillis) {
        String v = ifRange.trim();
        // ETag 형태 — quoted
        if (v.startsWith("\"") || v.startsWith("W/")) {
            // If-Range 는 강 검증자만 허용 (RFC 7233 §3.2). W/ 는 보수적으로 거부.
            if (v.startsWith("W/")) return false;
            return v.equals(etag);
        }
        // HTTP-date 형태
        long ims = parseHttpDate(v);
        return ims > 0 && lastModifiedMillis == ims;
    }

    /** RFC 1123 HTTP-date 포맷. */
    private static String formatHttpDate(long epochMillis) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC));
    }

    /**
     * RFC 1123 우선 + RFC 1036 / asctime 폴백 파싱. 실패 시 0.
     */
    private static long parseHttpDate(String header) {
        if (header == null || header.isBlank()) return 0;
        String h = header.trim();
        for (DateTimeFormatter f : HTTP_DATE_FORMATTERS) {
            try {
                return ZonedDateTime.parse(h, f).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return 0;
    }

    private static final DateTimeFormatter[] HTTP_DATE_FORMATTERS = {
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC),
    };

    /**
     * {@code bytes=start-end} 단일 구간만 지원. null 반환 = 전체 응답.
     */
    private static long[] parseRange(String header, long total) {
        if (header == null || !header.startsWith("bytes=")) return null;
        String spec = header.substring(6).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) return null;
        try {
            String s = spec.substring(0, dash).trim();
            String e = spec.substring(dash + 1).trim();
            long start, end;
            if (s.isEmpty()) {
                // suffix-length — 마지막 N 바이트
                long suffix = Long.parseLong(e);
                start = Math.max(0, total - suffix);
                end = total - 1;
            } else {
                start = Long.parseLong(s);
                end = e.isEmpty() ? total - 1 : Long.parseLong(e);
            }
            return new long[]{start, end};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 기본 attachment disposition 헤더 — 기존 호출자 호환용. */
    private static String contentDisposition(String originalName) {
        return contentDisposition(originalName, /*inline=*/ false);
    }

    /**
     * RFC 5987 대응 Content-Disposition 헤더값. 한글·공백·특수문자 포함 파일명 안전.
     *
     * @param inline true 면 {@code inline}, false 면 {@code attachment} (브라우저 다운로드)
     */
    private static String contentDisposition(String originalName, boolean inline) {
        String fallback = (originalName == null) ? "download" : originalName;
        // ASCII fallback — 비-ASCII는 "_" 로 치환
        StringBuilder asciiSafe = new StringBuilder();
        for (int i = 0; i < fallback.length(); i++) {
            char c = fallback.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '"' || c == '\\') {
                asciiSafe.append('_');
            } else if (c > 0x7F) {
                asciiSafe.append('_');
            } else {
                asciiSafe.append(c);
            }
        }
        String encoded = URLEncoder.encode(fallback, StandardCharsets.UTF_8).replace("+", "%20");
        String type = inline ? "inline" : "attachment";
        return type + "; filename=\"" + asciiSafe + "\"; filename*=UTF-8''" + encoded;
    }
}
