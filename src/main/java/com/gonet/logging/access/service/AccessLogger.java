package com.gonet.logging.access.service;

/**
 * log_access INSERT 진입점.
 *
 * <p>{@link com.gonet.config.filter.AccessLogFilter} 가 응답 commit 후 호출 →
 * 비동기 + REQUIRES_NEW TX 로 logging DB 에 기록. 실패해도 응답에 영향 없음.
 *
 * <p>signature: {@link AccessLogSnapshot} 1개. snapshot 은 {@link com.gonet.config.filter.AccessLogFilter}
 * 가 요청 스레드(=라이브 request 객체)에서 동기 캡처해서 넘긴다. async 워커는
 * snapshot 만 읽으므로 Tomcat 이 request facade 를 recycle 해도 안전.
 *
 * <p>이전 시그니처는 {@code log(HttpServletRequest, ...)} 였으나, async 워커가
 * 이미 recycle 된 request 객체를 만지면서 매 요청마다
 * {@code IllegalStateException: The request object has been recycled} 가 발생 (2026-04-27 회귀).
 */
public interface AccessLogger {

    /** 캡처된 스냅샷을 비동기로 logging DB 에 INSERT. */
    void log(AccessLogSnapshot snapshot);
}
