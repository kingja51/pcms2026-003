package com.gonet.common.file.service;

import com.gonet.common.file.config.FileUploadProperties;
import com.gonet.common.file.dto.UploadCategory;
import com.gonet.common.file.dto.UploadCommit;
import com.gonet.common.file.security.FileExtensionValidator;
import com.gonet.common.file.security.FileStorage;
import com.gonet.common.file.security.ImageReencoder;
import com.gonet.common.file.security.Sha256Hasher;
import com.gonet.common.file.security.TikaMimeDetector;
import com.gonet.common.file.security.UnauthenticatedUploadException;
import com.gonet.common.file.security.UploadValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * {@link FileUploadService} 구현 — 6중 방어 스택 orchestration (DB 비의존).
 *
 * <p>파이프라인:
 * <ol>
 *   <li>확장자 화이트리스트 (카테고리 제한)</li>
 *   <li>Tika 매직바이트 검증 (카테고리별 MIME 매칭 확인)</li>
 *   <li>격리 저장소({@code quarantine}) 에 기록</li>
 *   <li>이미지 재인코딩 (카테고리=IMAGE 이고 픽셀 수 제한 이내)</li>
 *   <li>SHA-256 해시 계산</li>
 *   <li>정식 저장소 승격 (ATOMIC_MOVE)</li>
 *   <li>썸네일 생성 (카테고리=IMAGE 만)</li>
 * </ol>
 *
 * <p>실패 시 격리본/승격본을 정리한 후 예외 재발생.
 * 도메인 측은 {@link UploadCommit} 을 받아 DB 기록 + 감사 이벤트 발행을 담당.
 */
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadServiceImpl.class);

    private final FileExtensionValidator validator;
    private final TikaMimeDetector       mimeDetector;
    private final FileStorage            storage;
    private final ImageReencoder         reencoder;
    private final Sha256Hasher           hasher;
    private final FileUploadProperties   properties;

    public FileUploadServiceImpl(FileExtensionValidator validator,
                                 TikaMimeDetector mimeDetector,
                                 FileStorage storage,
                                 ImageReencoder reencoder,
                                 Sha256Hasher hasher,
                                 FileUploadProperties properties) {
        this.validator    = validator;
        this.mimeDetector = mimeDetector;
        this.storage      = storage;
        this.reencoder    = reencoder;
        this.hasher       = hasher;
        this.properties   = properties;
    }

    @Override
    public UploadCommit uploadDocument(MultipartFile file, String directory) {
        return pipeline(file, directory, UploadCategory.DOC);
    }

    @Override
    public UploadCommit uploadImage(MultipartFile file, String directory) {
        return pipeline(file, directory, UploadCategory.IMAGE);
    }

    @Override
    public UploadCommit uploadVideo(MultipartFile file, String directory) {
        return pipeline(file, directory, UploadCategory.VIDEO);
    }

    @Override
    public UploadCommit uploadAny(MultipartFile file, String directory) {
        return pipeline(file, directory, UploadCategory.ANY);
    }

    // ------------------------------------------------------------------
    // 공통 파이프라인
    // ------------------------------------------------------------------

    private UploadCommit pipeline(MultipartFile mf, String directory, UploadCategory category) {
        Objects.requireNonNull(mf, "MultipartFile");
        Objects.requireNonNull(category, "UploadCategory");

        // 0) 인증 가드 — 로그인한 USER 만 업로드 가능 (Controller 우회 호출 포함 모든 경로 차단)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
            || "anonymousUser".equals(auth.getPrincipal())) {
            log.info("===FILE_UPLOAD_DENY unauthenticated category={} original={}",
                category, mf.getOriginalFilename());
            throw new UnauthenticatedUploadException("파일 업로드는 로그인 후 이용 가능합니다.");
        }

        // 1) 확장자 (카테고리 제한)
        String extension = validator.validate(mf.getOriginalFilename(), category);

        // 2) Tika 매직바이트
        String detectedMime;
        try (InputStream in = new BufferedInputStream(mf.getInputStream())) {
            detectedMime = mimeDetector.detectAndValidate(in, extension);
        } catch (IOException ex) {
            throw new UploadValidationException("파일 스트림 읽기 실패: " + ex.getMessage(), ex);
        }

        // 카테고리별 Tika MIME family 확인 — 확장자를 우회한 폴리글롯 방어
        enforceCategoryMime(category, detectedMime);

        // "이미지로 판정" — 확장자(image-extensions) OR Tika MIME (image/*) 어느 쪽이든 true.
        // 둘 다 체크하는 이유: 업로더 확장자 오기재(webp 를 jpg 로 저장) 및 Tika 가
        // 특수 이미지 포맷을 application/octet-stream 으로 반환하는 코너 케이스를 모두 포괄.
        boolean isImageByExt  = isImageExtension(extension);
        boolean isImageByMime = TikaMimeDetector.isImage(detectedMime);
        boolean isImage       = isImageByExt || isImageByMime;

        // 3) 격리 저장
        FileStorage.QuarantineSaved q = storage.saveToQuarantine(mf, extension, directory);
        Path quarantined = q.path();

        Path promotedAbs = null;
        try {
            boolean reencoded = false;
            // 4) 이미지 재인코딩 — 카테고리 제약 제거. 이미지면 가능한 한 재인코딩(polyglot 방어).
            //    대용량(픽셀 수 초과) 은 reencoder 내부에서 skip 처리됨.
            if (isImage) {
                try {
                    ImageReencoder.Result rr = reencoder.reencodeIfImage(quarantined, extension);
                    reencoded = rr.applied;
                } catch (UploadValidationException ex) {
                    // 손상·지원 불가 포맷은 여기까지 왔다면 이미 MIME 검증은 통과한 상태.
                    // 카테고리가 IMAGE 가 아니었다면(ANY) 재인코딩 실패를 치명으로 보지 않고 원본 보존.
                    if (category == UploadCategory.IMAGE) throw ex;
                    log.info("===FILE_UPLOAD reencode skipped (non-IMAGE category) reason={}",
                        ex.getMessage());
                }
            }

            // 5) SHA-256
            long   sizeBytes = safeSize(quarantined);
            String fileHash  = hasher.hash(quarantined);

            // 6) 정식 저장소 승격
            String relative = storage.promoteToRoot(quarantined, q.relative());
            promotedAbs = storage.resolveStoredPath(relative);

            // 7) 썸네일은 엔진에서 생성하지 않는다 — primary 도메인이 INSERT 커밋 후
            //    AsyncThumbnailService 로 비동기 호출. thumbnailPath=null 로 반환.
            log.info("===FILE_UPLOAD_OK category={} original={} size={} ext={} mime={} reencoded={}",
                category, mf.getOriginalFilename(), sizeBytes, extension, detectedMime, reencoded);

            return new UploadCommit(
                mf.getOriginalFilename(), q.storedName(), relative, null,
                extension, detectedMime, mf.getContentType(),
                sizeBytes, fileHash, isImage, reencoded, category);
        } catch (RuntimeException ex) {
            storage.deleteQuietly(quarantined);
            if (promotedAbs != null) storage.deleteQuietly(promotedAbs);
            throw ex;
        }
    }

    private static void enforceCategoryMime(UploadCategory category, String detectedMime) {
        if (detectedMime == null) return;
        String mime = detectedMime.toLowerCase(Locale.ROOT);
        boolean ok = switch (category) {
            case IMAGE -> mime.startsWith("image/");
            case VIDEO -> mime.startsWith("video/");
            case DOC   -> !mime.startsWith("image/") && !mime.startsWith("video/");
            case ANY   -> true;
        };
        if (!ok) {
            throw new UploadValidationException(
                "요청한 카테고리(" + category + ") 와 파일 형식이 일치하지 않습니다: " + detectedMime);
        }
    }

    /**
     * 파일 확장자가 {@code gopcms.file.upload.image-extensions} 목록에 속하는지.
     * null/blank 는 false. 소문자 정규화 후 비교.
     */
    private boolean isImageExtension(String extension) {
        if (extension == null || extension.isBlank()) return false;
        return properties.imageExtensionSet().contains(extension.toLowerCase(Locale.ROOT));
    }

    private static long safeSize(Path p) {
        try { return Files.size(p); } catch (IOException ex) { return -1L; }
    }
}
