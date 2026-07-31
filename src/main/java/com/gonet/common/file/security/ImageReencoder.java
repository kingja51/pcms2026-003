package com.gonet.common.file.security;

import com.gonet.common.file.config.FileUploadProperties;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;

/**
 * 방어 #3 — Thumbnailator 이미지 재인코딩.
 *
 * <p>원본 비트를 버리고 디코드→재인코딩해 stegosploit/polyglot 방어. 원본 크기 유지, JPEG 0.92.
 *
 * <p>대용량 가드 — 휴대폰 고해상도 사진 / 4K 캡처 이미지는 수천만~수억 픽셀로 OOM 유발 가능.
 * Thumbnailator 가 전체를 메모리에 decode 하므로 {@code reencode-max-pixels} 를 초과하면
 * 재인코딩 skip 하고 원본 그대로 저장. SHA-256 FIM + ClamAV 로 기본 보호는 유지.
 *
 * <p>webp / 동영상 / 기타 비-이미지: skip (applied=false).
 */
@Component
public class ImageReencoder {

    private static final Logger log = LoggerFactory.getLogger(ImageReencoder.class);

    private final FileUploadProperties properties;

    public ImageReencoder(FileUploadProperties properties) {
        this.properties = properties;
    }

    public static final class Result {
        public final boolean applied;
        public final long newSizeBytes;
        public Result(boolean applied, long newSizeBytes) {
            this.applied = applied;
            this.newSizeBytes = newSizeBytes;
        }
    }

    public Result reencodeIfImage(Path path, String extension) {
        if (extension == null) return new Result(false, safeSize(path));
        String ext = extension.toLowerCase(Locale.ROOT);
        if (!isReencodable(ext)) {
            return new Result(false, safeSize(path));
        }

        // 대용량 가드 — 전체 decode 전에 dimension 을 빠르게 읽어 픽셀 수 확인
        long pixels = safeReadPixelCount(path);
        if (pixels > properties.getReencodeMaxPixels()) {
            log.info("===IMAGE_REENCODE_SKIP large-image path={} pixels={} limit={}",
                path, pixels, properties.getReencodeMaxPixels());
            return new Result(false, safeSize(path));
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Thumbnails.of(path.toFile())
                .scale(1.0)
                .outputFormat(outputFormat(ext))
                .outputQuality(0.92)
                .toOutputStream(baos);

            byte[] bytes = baos.toByteArray();
            if (bytes.length == 0) {
                throw new IOException("재인코딩 결과가 비어있음");
            }
            Files.write(path, bytes,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            log.info("IMAGE_REENCODED path={} ext={} size={}", path, ext, bytes.length);
            return new Result(true, bytes.length);
        } catch (IOException | UnsupportedOperationException | OutOfMemoryError ex) {
            log.warn("IMAGE_REENCODE_FAILED path={} ext={} reason={}",
                path, ext, ex.getMessage(), ex);
            throw new UploadValidationException(
                "이미지 재인코딩에 실패했습니다. 파일이 손상되었거나 지원되지 않는 포맷입니다.", ex);
        }
    }

    private static boolean isReencodable(String ext) {
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif" -> true;
            // webp, 동영상, 문서는 skip
            default -> false;
        };
    }

    private static String outputFormat(String ext) {
        return switch (ext) {
            case "jpg" -> "jpeg";
            default -> ext;
        };
    }

    /**
     * ImageIO reader 로 dimension 만 빠르게 읽기 (전체 decode 안 함).
     * 실패하거나 reader 없으면 -1 반환 → 게이트 통과 (skip 판정은 보수적으로 통과 선택).
     */
    private static long safeReadPixelCount(Path path) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
            if (iis == null) return -1L;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return -1L;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                return (long) w * (long) h;
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            return -1L;
        }
    }

    private static long safeSize(Path p) {
        try { return Files.size(p); } catch (IOException ex) { return -1L; }
    }
}
