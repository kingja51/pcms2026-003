package com.gonet.common.file.security;

/**
 * 파일 업로드 방어 스택에서 차단된 업로드.
 *
 * <p>Controller 는 이 예외를 400(Bad Request) 으로 매핑. 감사 로그에는
 * {@code UPLOAD_REJECTED} 이벤트로 기록되어야 한다 (호출 측 책임).
 */
public class UploadValidationException extends RuntimeException {

    public UploadValidationException(String reason) {
        super(reason);
    }

    public UploadValidationException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
