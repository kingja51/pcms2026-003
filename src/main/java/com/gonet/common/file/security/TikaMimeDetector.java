package com.gonet.common.file.security;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

/**
 * 방어 #2 — Tika 매직바이트 MIME 검출.
 *
 * <p>클라이언트가 보내는 {@code Content-Type} 은 위조 가능하므로 무시하고 파일 바이트 헤더
 * 를 Tika 로 직접 스캔해 실제 MIME 을 확정한다. 그 결과가 확장자와 일치하지 않으면 거부
 * (예: `.jpg` 업로드인데 실제 매직바이트가 `application/x-msdownload` 면 차단).
 */
@Component
public class TikaMimeDetector {

    private static final Logger log = LoggerFactory.getLogger(TikaMimeDetector.class);

    private static final Tika TIKA = new Tika();

    /**
     * 확장자 ↔ 허용 가능한 MIME 매핑. 실제 Tika 결과가 여기 목록에 포함되어야 통과.
     * 복수 MIME 허용(예: docx 은 ms-office / zip / ooxml 등 환경별 차이).
     */
    private static final Map<String, Map<String, Boolean>> EXPECTED = Map.ofEntries(
        entry("jpg",  "image/jpeg", "image/jpg"),
        entry("jpeg", "image/jpeg", "image/jpg"),
        entry("png",  "image/png"),
        entry("gif",  "image/gif"),
        entry("webp", "image/webp"),
        entry("pdf",  "application/pdf"),
        // HWP 5.x 는 CFB(OLE2) 컨테이너 — Tika 버전/환경에 따라 검출 결과가 다양:
        //   - application/x-hwp-v5         : Tika 2.x (정확)
        //   - application/x-hwp            : 일부 Tika 빌드
        //   - application/haansofthwp      : 한컴 공식
        //   - application/vnd.hancom.hwp   : 일부 환경
        //   - application/x-tika-msoffice  : Tika 가 OLE2 만 식별하고 HWP 시그니처 모를 때 (가장 흔함)
        // application/octet-stream 은 너무 광범위해 의도적으로 제외 — PHP 텍스트 위장 등 방어.
        entry("hwp",  "application/x-hwp", "application/x-hwp-v5",
                     "application/haansofthwp", "application/vnd.hancom.hwp",
                     "application/x-tika-msoffice"),
        // HWPX 는 ZIP 컨테이너 — OOXML 폴백 포함
        entry("hwpx", "application/hwp+zip", "application/vnd.hancom.hwpx",
                     "application/zip", "application/x-tika-ooxml"),
        entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                     "application/x-tika-ooxml", "application/zip"),
        entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                     "application/x-tika-ooxml", "application/zip"),
        entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                     "application/x-tika-ooxml", "application/zip"),
        entry("zip",  "application/zip", "application/x-zip-compressed"),
        entry("txt",  "text/plain"),
        // 휴대폰 동영상
        entry("mp4",  "video/mp4", "video/quicktime"),
        entry("mov",  "video/quicktime", "video/mp4"),
        entry("webm", "video/webm")
    );

    private static Map.Entry<String, Map<String, Boolean>> entry(String ext, String... mimes) {
        Map<String, Boolean> m = new java.util.HashMap<>();
        for (String mime : mimes) m.put(mime, Boolean.TRUE);
        return Map.entry(ext, m);
    }

    /**
     * 스트림을 읽어 실제 MIME 을 반환. 확장자와 불일치 시 예외.
     *
     * <p>{@code InputStream} 은 호출 측이 mark/reset 지원 또는 buffer 된 상태여야 한다
     * (Tika 가 헤더 몇 KB 만 읽으므로 stream 이 이후에도 사용 가능해야 함).
     */
    public String detectAndValidate(InputStream in, String normalizedExtension) {
        String detected;
        try {
            detected = TIKA.detect(in);
        } catch (IOException ex) {
            throw new UploadValidationException("파일 MIME 검출 실패: " + ex.getMessage(), ex);
        }
        if (detected == null || detected.isBlank()) {
            throw new UploadValidationException("파일 MIME 을 판별할 수 없습니다.");
        }

        String detectedLower = detected.toLowerCase(Locale.ROOT);
        Map<String, Boolean> allowed = EXPECTED.get(normalizedExtension);
        if (allowed == null) {
            log.warn("FILE_UPLOAD_REJECTED no-mime-policy ext={}", normalizedExtension);
            throw new UploadValidationException(
                "확장자에 대한 MIME 검증 정책이 없습니다: " + normalizedExtension);
        }
        if (!allowed.containsKey(detectedLower)) {
            log.warn("FILE_UPLOAD_REJECTED mime-mismatch ext={} detected={}",
                normalizedExtension, detected);
            throw new UploadValidationException(
                "확장자와 실제 파일 형식이 일치하지 않습니다: ext=" + normalizedExtension
                + ", detected=" + detected);
        }
        return detected;
    }

    /** 이미지 여부 판정 — 재인코딩 적용 대상. */
    public static boolean isImage(String detectedMime) {
        return detectedMime != null && detectedMime.toLowerCase(Locale.ROOT).startsWith("image/");
    }
}
