package com.gonet.common.file.service;

import com.gonet.common.web.ResourceNotFoundException;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * JODConverter 기반 문서 변환 — LocalOfficeManager 가 관리하는 LibreOffice 풀에 변환 요청.
 *
 * <p>활성 조건:
 * <ul>
 *   <li>{@code gopcms.file.converter.enabled=true}</li>
 *   <li>JODConverter starter 가 {@link DocumentConverter} 빈 자동 등록 — {@code jodconverter.local.enabled=true} 필요</li>
 *   <li>운영 서버에 LibreOffice 헤드리스 설치</li>
 * </ul>
 * 셋 중 하나라도 안 맞으면 빈 미주입 → 호출 측 Optional 이 empty.
 *
 * <p>캐시 전략: 원본의 SHA-256 을 키로 {@code <cacheRoot>/{ab}/{cdef...}.pdf} (256-fanout) 저장.
 * 같은 파일이 (다른 fileId 로) 여러 번 업로드되어도 hash 동일하면 캐시 hit.
 */
@Service
@ConditionalOnProperty(prefix = "gopcms.file.converter", name = "enabled", havingValue = "true")
public class DocumentConvertServiceImpl implements DocumentConvertService {

    private static final Logger log = LoggerFactory.getLogger(DocumentConvertServiceImpl.class);
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private final DocumentConverter converter;
    private final Path cacheRoot;

    public DocumentConvertServiceImpl(DocumentConverter converter,
                                      @Value("${gopcms.file.converter.cache-root}") String cacheRootPath) {
        this.converter = converter;
        this.cacheRoot = Paths.get(cacheRootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.cacheRoot);
            log.info("===DocumentConvertService 활성 — cacheRoot={}", this.cacheRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                "변환 캐시 디렉터리 생성 실패: " + this.cacheRoot, e);
        }
    }

    @Override
    public Path ensurePdf(Path sourcePath, String fileHash) {
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            throw new ResourceNotFoundException("원본 파일이 존재하지 않습니다: " + sourcePath);
        }
        if (fileHash == null || !SHA256_HEX.matcher(fileHash.toLowerCase()).matches()) {
            throw new IllegalArgumentException("유효하지 않은 file hash: " + fileHash);
        }

        Path cachePath = resolveCachePath(fileHash.toLowerCase());

        // 캐시 hit — 즉시 반환
        if (Files.isRegularFile(cachePath)) {
            log.info("PDF 변환 캐시 hit hash={}", fileHash);
            return cachePath;
        }

        // 부모 디렉터리 보장
        try {
            Files.createDirectories(cachePath.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("캐시 부모 디렉터리 생성 실패: " + cachePath.getParent(), e);
        }

        // 임시 파일에 변환 후 atomic rename — 동시 변환 시 부분 파일 노출 방지
        Path tmpPath = cachePath.resolveSibling(cachePath.getFileName() + ".tmp." + System.nanoTime());

        long startMs = System.currentTimeMillis();
        try {
            // 명시적으로 target 형식을 PDF 로 지정 — tmp 파일명이 .tmp.{nanoTime} 으로 끝나
            // JODConverter 가 확장자로 형식 추정 못 해 NPE("The target format is missing") 발생.
            // .as(PDF) 로 우회.
            converter.convert(sourcePath.toFile())
                .to(tmpPath.toFile())
                .as(DefaultDocumentFormatRegistry.PDF)
                .execute();
            Files.move(tmpPath, cachePath,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("===PDF 변환 완료 hash={} elapsedMs={} src={} dst={}",
                fileHash, elapsedMs, sourcePath.getFileName(), cachePath.getFileName());
            return cachePath;
        } catch (Exception e) {
            // 부분 파일 정리
            try { Files.deleteIfExists(tmpPath); } catch (IOException ignored) {}
            // 풀 stack trace + cause chain 모두 로그 — JODConverter 의 OfficeException 은 보통
            // cause 안에 진짜 이유(LibreOffice not found / port 충돌 / file lock 등)가 있음
            log.warn("PDF 변환 실패 hash={} src={}", fileHash, sourcePath.getFileName(), e);
            String reason = describeCauseChain(e);
            throw new RuntimeException("문서 변환 실패: " + reason, e);
        }
    }

    /** Exception 의 메시지 + 모든 cause 의 메시지를 ' <- ' 로 연결. 진단용. */
    private static String describeCauseChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 8) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(cur.getClass().getSimpleName()).append(": ").append(cur.getMessage());
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    /**
     * 캐시 파일 경로 — SHA-256 의 첫 2자를 디렉터리 (256-way fanout) 로 사용해 한 디렉터리에 너무 많은
     * 파일이 쌓이지 않도록.
     * 예: {@code <root>/ab/cdef0123....pdf}
     */
    private Path resolveCachePath(String hash) {
        return cacheRoot.resolve(hash.substring(0, 2)).resolve(hash + ".pdf");
    }
}
