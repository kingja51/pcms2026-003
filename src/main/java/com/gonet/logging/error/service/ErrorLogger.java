package com.gonet.logging.error.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * log_error INSERT 진입점.
 *
 * <p>{@link com.gonet.config.access.ErrorLoggingExceptionResolver} 가 모든 미처리 예외에서
 * 호출. 비동기 + REQUIRES_NEW TX 로 logging DB 에 기록 — 예외 응답 흐름과 분리.
 */
public interface ErrorLogger {

    /**
     * 예외 1건 비동기 기록.
     * @param req         원본 request (URI/IP/UA/세션 추출)
     * @param ex          throwable — class/message/stackTrace 추출
     * @param statusCode  응답 status (HandlerExceptionResolver 단계에선 보통 500)
     */
    void log(HttpServletRequest req, Throwable ex, Integer statusCode);
}
