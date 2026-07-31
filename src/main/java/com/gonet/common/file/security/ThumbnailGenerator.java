package com.gonet.common.file.security;

import com.gonet.common.file.config.FileUploadProperties;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 이미지 썸네일 생성기 — 항상 JPG, 고정 너비(픽셀), aspect ratio 유지.
 *
 * <p>비-이미지/webp(JDK 기본 ImageIO 미지원)/대용량 이미지는 skip — null 반환.
 * 썸네일 파일은 {@link FileStorage#thumbnailTarget(String, String)} 이 지정한
 * 절대 경로에 기록되고, 저장 결과의 상대 경로는 {@code tb_file.thumbnail_path} 로 보관.
 */
@Component
public class ThumbnailGenerator {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailGenerator.class);

    private final FileUploadProperties properties;

    public ThumbnailGenerator(FileUploadProperties properties) {
        this.properties = properties;
    }

    /**
     * 원본 이미지로부터 썸네일을 생성하여 {@code targetAbsolute} 에 저장.
     *
     * @param sourceAbsolute  원본 절대 경로
     * @param extension       정규화된 확장자 (jpg/jpeg/png/gif/webp)
     * @param targetAbsolute  썸네일 저장 절대 경로 (확장자 .jpg)
     * @return {@code true} 생성 성공, {@code false} skip (비이미지/webp/실패 등)
     */
    public boolean generate(Path sourceAbsolute, String extension, Path targetAbsolute) {
        if (extension == null) return false;
        String ext = extension.toLowerCase(Locale.ROOT);
        if (!isSupported(ext)) {
            log.info("THUMBNAIL_SKIP unsupported-ext ext={}", ext);
            return false;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Thumbnails.of(sourceAbsolute.toFile())
                .width(properties.getThumbnailWidth())
                .outputFormat("jpeg")
                .outputQuality(properties.getThumbnailQuality())
                .toOutputStream(baos);
            Files.write(targetAbsolute, baos.toByteArray());
            log.info("THUMBNAIL_OK source={} target={} bytes={}",
                sourceAbsolute, targetAbsolute, baos.size());
            return true;
        } catch (IOException | UnsupportedOperationException | OutOfMemoryError ex) {
            log.warn("THUMBNAIL_FAIL source={} reason={}", sourceAbsolute, ex.getMessage());
            // 썸네일 실패는 업로드 자체를 막지 않음 — 원본은 저장됨
            return false;
        }
    }

    private static boolean isSupported(String ext) {
        // webp 는 JDK 기본 ImageIO 쓰기 미지원 → 썸네일 skip
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif" -> true;
            default -> false;
        };
    }
}
