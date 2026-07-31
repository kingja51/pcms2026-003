package com.gonet.common.file.security;

import com.gonet.common.file.config.FileUploadProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 방어 #5 — 격리 저장소 + 정식 저장소 + 썸네일 저장소 디렉터리 관리.
 *
 * <p>저장 경로 규칙 (entityType 기준 3-way 디렉터리 구조):
 * <ul>
 *   <li>{@code ETC} 또는 null/blank → {@code {root}/{yyyy}/{MM}/{stored}}</li>
 *   <li>그 외 → {@code {root}/{ENTITY_TYPE}/{yyyy}/{MM}/{stored}}</li>
 * </ul>
 *
 * <p>동일 규칙이 quarantine / upload root / thumbnail root 에 모두 적용된다.
 *
 * <p>반환되는 {@code relative} 는 각 root 기준 상대경로로, DB {@code tb_file.stored_path} /
 * {@code tb_file.thumbnail_path} 에 저장된다. 다운로드 시 root + relative 로 절대 경로 복원.
 */
@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy/MM");

    private final FileUploadProperties properties;

    private Path quarantineRoot;
    private Path uploadRoot;
    private Path thumbnailRoot;

    public FileStorage(FileUploadProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        // 절대경로 + normalize 로 고정 — resolveWithin 의 containment(startsWith) 기준선.
        this.quarantineRoot = Paths.get(requireNonBlank(properties.getQuarantine(), "quarantine")).toAbsolutePath().normalize();
        this.uploadRoot     = Paths.get(requireNonBlank(properties.getRoot(),       "root")).toAbsolutePath().normalize();
        this.thumbnailRoot  = Paths.get(requireNonBlank(properties.getThumbnail(),  "thumbnail")).toAbsolutePath().normalize();
        try {
            Files.createDirectories(quarantineRoot);
            Files.createDirectories(uploadRoot);
            Files.createDirectories(thumbnailRoot);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "파일 저장소 디렉터리 생성 실패: " + ex.getMessage(), ex);
        }
        log.info("===FILE_STORAGE_READY quarantine={} root={} thumbnail={}",
            quarantineRoot, uploadRoot, thumbnailRoot);
    }

    /**
     * 격리 저장 — 최초 업로드 위치. 경로: {@code {quarantine}/{TYPE?}/yyyy/MM/{stored}}.
     * 정식 승격 전이므로 반드시 quarantine 에만 존재.
     */
    public QuarantineSaved saveToQuarantine(MultipartFile mf, String extension, String entityType) {
        if (mf.isEmpty()) {
            throw new UploadValidationException("빈 파일은 업로드할 수 없습니다.");
        }
        if (mf.getSize() > properties.maxFileSizeBytes()) {
            throw new UploadValidationException(
                "파일 크기 한도를 초과했습니다. 최대 " + properties.getMaxFileSizeMb() + "MB");
        }

        String storedName = java.util.UUID.randomUUID() + "." + extension;
        String relative   = buildRelative(entityType, storedName);
        Path target = resolveWithin(quarantineRoot, relative);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = mf.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("격리 저장 실패: " + ex.getMessage(), ex);
        }
        return new QuarantineSaved(target, storedName, relative);
    }

    /**
     * 격리 → 정식 root 이동. 반환: DB 에 기록할 상대경로 (격리 측 relative 동일 규칙 재사용).
     */
    public String promoteToRoot(Path quarantined, String relative) {
        Path target = resolveWithin(uploadRoot, relative);
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.move(quarantined, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFail) {
                // ATOMIC_MOVE 미지원 볼륨(크로스 파티션) fallback
                Files.move(quarantined, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("정식 저장소 이동 실패: " + ex.getMessage(), ex);
        }
        return relative.replace('\\', '/');
    }

    /**
     * 썸네일 저장 대상 절대 경로 반환 ({@link #thumbnailRoot} + relative). 부모 디렉터리는 자동 생성.
     * 썸네일 파일명은 입력 stored name 에서 확장자만 jpg 로 치환.
     */
    public ThumbnailTarget thumbnailTarget(String entityType, String storedName) {
        // storedName = "uuid.jpeg" → jpgName = "uuid.jpg"
        int dot = storedName.lastIndexOf('.');
        String base = (dot > 0) ? storedName.substring(0, dot) : storedName;
        String jpgName = base + ".jpg";
        String relative = buildRelative(entityType, jpgName);

        Path absolute = resolveWithin(thumbnailRoot, relative);
        try {
            Files.createDirectories(absolute.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("썸네일 디렉터리 생성 실패: " + ex.getMessage(), ex);
        }
        return new ThumbnailTarget(absolute, relative.replace('\\', '/'));
    }

    /** 검증 실패 시 격리본 정리. 예외는 먹음. */
    public void deleteQuietly(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ex) {
            log.warn("STORAGE_CLEANUP_FAILED path={} reason={}", p, ex.getMessage());
        }
    }

    public Path resolveStoredPath(String relative) {
        return resolveWithin(uploadRoot, relative);
    }

    public Path resolveThumbnailPath(String relative) {
        return resolveWithin(thumbnailRoot, relative);
    }

    public Path resolveQuarantinePath(String relative) {
        return resolveWithin(quarantineRoot, relative);
    }

    /**
     * {@code relative} 을 {@code root} 기준으로 resolve 하되, 정규화 후 root 밖으로
     * 벗어나면 차단. 저장·조회 양방향 경로탐색(../, 절대경로, 심볼릭 우회)의 최종 방어선.
     */
    private static Path resolveWithin(Path root, String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new UploadValidationException("허용되지 않은 저장 경로입니다.");
        }
        return resolved;
    }

    // ------------------------------------------------------------------
    // 경로 규칙
    // ------------------------------------------------------------------

    /**
     * {@code {TYPE?}/yyyy/MM/{fileName}} 형태의 상대 경로를 생성.
     * entityType 이 null/blank/ETC 면 type 디렉터리 생략.
     */
    private static String buildRelative(String entityType, String fileName) {
        String type = normalizeType(entityType);
        String yyyymm = LocalDate.now().format(YYYY_MM);
        return (type == null ? "" : type + "/") + yyyymm + "/" + fileName;
    }

    /** entityType/디렉터리 토큰 화이트리스트 — enum 계열 대문자·숫자·언더스코어만 허용. */
    private static final java.util.regex.Pattern SAFE_TYPE =
        java.util.regex.Pattern.compile("^[A-Z0-9_]+$");

    private static String normalizeType(String entityType) {
        if (entityType == null) return null;
        String t = entityType.trim().toUpperCase();
        if (t.isEmpty() || "ETC".equals(t)) return null;
        // 경로 구분자·상위탐색(../)·기타 문자 차단 — API 의 directory 파라미터가
        // entityType 을 우회해 임의 경로로 흘러들어오는 것을 원천 차단.
        if (!SAFE_TYPE.matcher(t).matches()) {
            throw new UploadValidationException("허용되지 않은 저장 구분자입니다: " + entityType);
        }
        return t;
    }

    private static String requireNonBlank(String s, String name) {
        if (s == null || s.isBlank()) {
            throw new IllegalStateException("gopcms.file.upload." + name + " 설정이 비어있습니다.");
        }
        return s;
    }

    // ------------------------------------------------------------------
    // 결과 레코드
    // ------------------------------------------------------------------

    /**
     * 격리 저장 결과.
     *
     * @param path       절대 경로 (quarantine 절대)
     * @param storedName 저장 파일명 (UUID 기반, 확장자 포함)
     * @param relative   격리·정식 공통 상대경로 ({@code TYPE?/yyyy/MM/stored})
     */
    public record QuarantineSaved(Path path, String storedName, String relative) {}

    /** 썸네일 저장 대상. */
    public record ThumbnailTarget(Path absolute, String relative) {}
}
