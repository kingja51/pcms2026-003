package com.gonet.common.file.security;

/**
 * 비인증 업로드 시도.
 *
 * <p>업로드는 보안상 반드시 로그인한 사용자만 허용. {@code SecurityContextHolder} 에
 * 유효한 {@code Authentication} 이 없거나 익명(anonymousUser)이면 엔진이 이 예외를 던진다.
 *
 * <p>Controller 는 이 예외를 401(Unauthorized) 로 매핑한다.
 */
public class UnauthenticatedUploadException extends RuntimeException {

    public UnauthenticatedUploadException(String reason) {
        super(reason);
    }
}
