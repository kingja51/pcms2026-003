package com.gonet.common.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 파일 업로드 보안 설정 — {@code application.yml} 의 {@code gopcms.file.upload.*} 바인딩.
 *
 * <p>확장자 카테고리:
 * <ul>
 *   <li>{@code allowedExtensions} — 최종 허용 화이트리스트 (카테고리의 합집합)</li>
 *   <li>{@code docExtensions}     — 문서 전용 업로드 대상</li>
 *   <li>{@code imageExtensions}   — 이미지 전용 업로드 대상 (썸네일·재인코딩 후보)</li>
 *   <li>{@code videoExtensions}   — 동영상 전용 업로드 대상 (큰 파일, 썸네일 skip)</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "gopcms.file.upload")
@Getter
@Setter
public class FileUploadProperties {

    private String root;
    private String quarantine;
    private String thumbnail;

    private List<String> allowedExtensions;
    private List<String> docExtensions;
    private List<String> imageExtensions;
    private List<String> videoExtensions;

    private int maxFileSizeMb = 200;

    /** 썸네일 너비 (픽셀). 기본 400. */
    private int thumbnailWidth = 400;

    /** 썸네일 JPEG 품질 0.0~1.0. 기본 0.85. */
    private double thumbnailQuality = 0.85;

    /**
     * 이미지 재인코딩 skip 기준 — 픽셀 수(width*height)가 이 값보다 크면 재인코딩 skip.
     * Thumbnailator 가 전체를 메모리에 decode 하므로 초대형 이미지는 OOM 위험.
     * 기본 1억 픽셀(10000x10000).
     */
    private long reencodeMaxPixels = 100_000_000L;

    /** 비동기 썸네일 생성 설정 (대용량/다수 파일 업로드 시 응답 지연 완화). */
    private ThumbnailAsync thumbnailAsync = new ThumbnailAsync();

    @Getter @Setter
    public static class ThumbnailAsync {
        /** 스레드풀 core 크기 (평상시 유지 스레드). */
        private int corePool = 2;
        /** 스레드풀 max 크기 (피크 시). */
        private int maxPool = 4;
        /** 큐 최대 수 — 초과 시 CallerRunsPolicy 로 업로드 스레드가 직접 실행(safe fallback). */
        private int queueCapacity = 100;
        /** 스레드 이름 prefix. */
        private String threadNamePrefix = "thumb-";
    }

    public Set<String> allowedExtensionSet() { return toLowerSet(allowedExtensions); }
    public Set<String> docExtensionSet()     { return toLowerSet(docExtensions); }
    public Set<String> imageExtensionSet()   { return toLowerSet(imageExtensions); }
    public Set<String> videoExtensionSet()   { return toLowerSet(videoExtensions); }

    public long maxFileSizeBytes() {
        return (long) maxFileSizeMb * 1024L * 1024L;
    }

    private static Set<String> toLowerSet(List<String> src) {
        if (src == null || src.isEmpty()) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>(src.size());
        for (String e : src) {
            if (e == null) continue;
            String trimmed = e.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}
