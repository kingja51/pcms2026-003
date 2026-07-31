package com.gonet.common.file.security;

import com.gonet.common.file.config.FileUploadProperties;
import com.gonet.common.file.dto.UploadCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 방어 #1 — 확장자 화이트리스트 + 카테고리 제한.
 *
 * <p>방어 포인트:
 * <ul>
 *   <li>허용 확장자 Set 에 포함되지 않으면 거부</li>
 *   <li>더블 확장자 공격(`foo.jpg.php`) — 파일명에 포함된 모든 조각을 검사해 실행 가능 확장자
 *       포함 시 즉시 거부</li>
 *   <li>null byte 주입·제어 문자·path separator 차단</li>
 *   <li>카테고리 지정 시 해당 카테고리 extension 만 허용 — uploadImage / uploadVideo / uploadDocument
 *       호출 경로에서 타 카테고리 확장자 혼입 차단</li>
 * </ul>
 */
@Component
public class FileExtensionValidator {

    private static final Logger log = LoggerFactory.getLogger(FileExtensionValidator.class);

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        "php", "phtml", "php3", "php4", "php5", "php7", "phps",
        "jsp", "jspx", "jspf",
        "asp", "aspx", "ashx", "cer",
        "cgi", "pl", "py", "rb", "sh", "bat", "cmd",
        "exe", "dll", "msi", "scr", "com",
        "htaccess", "htpasswd"
    );

    private final FileUploadProperties properties;

    public FileExtensionValidator(FileUploadProperties properties) {
        this.properties = properties;
    }

    /** 카테고리 무관 ANY 검증 (하위 호환). */
    public String validate(String originalFilename) {
        return validate(originalFilename, UploadCategory.ANY);
    }

    /**
     * 원본 파일명 검증 + 카테고리 제한 후 정규화된 확장자 반환.
     *
     * @throws UploadValidationException 차단 사유 포함
     */
    public String validate(String originalFilename, UploadCategory category) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new UploadValidationException("파일명이 비어있습니다.");
        }
        if (originalFilename.indexOf('\0') >= 0) {
            throw new UploadValidationException("파일명에 null byte 가 포함될 수 없습니다.");
        }
        for (int i = 0; i < originalFilename.length(); i++) {
            if (originalFilename.charAt(i) < 0x20) {
                throw new UploadValidationException("파일명에 제어 문자가 포함되어 있습니다.");
            }
        }
        if (originalFilename.indexOf('/') >= 0 || originalFilename.indexOf('\\') >= 0) {
            throw new UploadValidationException("파일명에 경로 구분자(/ \\)가 포함될 수 없습니다.");
        }

        String[] pieces = originalFilename.toLowerCase(Locale.ROOT).split("\\.");
        if (pieces.length < 2) {
            throw new UploadValidationException("확장자가 없는 파일은 허용되지 않습니다.");
        }
        for (int i = 1; i < pieces.length; i++) {
            String piece = pieces[i];
            if (DANGEROUS_EXTENSIONS.contains(piece)) {
                log.warn("FILE_UPLOAD_REJECTED dangerous-extension filename={} piece={}",
                    originalFilename, piece);
                throw new UploadValidationException(
                    "실행 가능한 확장자는 허용되지 않습니다: " + piece);
            }
        }

        String lastExt = pieces[pieces.length - 1];
        Set<String> categorySet = resolveCategorySet(category);
        if (categorySet.isEmpty()) {
            throw new UploadValidationException(
                "서버에 허용 확장자 설정이 비어있습니다(" + category + "). 운영자에게 문의하세요.");
        }
        if (!categorySet.contains(lastExt)) {
            log.info("===FILE_UPLOAD_REJECTED extension-not-allowed category={} ext={}",
                category, lastExt);
            throw new UploadValidationException(categoryDenyMessage(category, lastExt));
        }
        return lastExt;
    }

    private Set<String> resolveCategorySet(UploadCategory category) {
        return switch (category) {
            case DOC   -> properties.docExtensionSet();
            case IMAGE -> properties.imageExtensionSet();
            case VIDEO -> properties.videoExtensionSet();
            case ANY   -> properties.allowedExtensionSet();
        };
    }

    private static String categoryDenyMessage(UploadCategory category, String ext) {
        return switch (category) {
            case DOC   -> "문서 업로드에 허용되지 않은 확장자입니다: " + ext;
            case IMAGE -> "이미지 업로드에 허용되지 않은 확장자입니다: " + ext;
            case VIDEO -> "동영상 업로드에 허용되지 않은 확장자입니다: " + ext;
            case ANY   -> "허용되지 않은 확장자입니다: " + ext;
        };
    }
}
