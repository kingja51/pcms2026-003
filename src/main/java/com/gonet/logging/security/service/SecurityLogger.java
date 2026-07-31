package com.gonet.logging.security.service;

import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityLog;

/**
 * 보안 이벤트 분리 적재 단일 진입점 — log_security INSERT.
 *
 * <p>구현체는 비동기({@code @Async}) + REQUIRES_NEW 트랜잭션으로 INSERT —
 * 호출자(primary TX) 가 롤백되어도 이력은 보존된다. 보안 이벤트는 SOC/SIEM
 * 1차 소스이므로 적재 누락이 본 트랜잭션 흐름을 차단해선 안 된다.
 *
 * <p>호출 패턴 ({@link com.gonet.common.audit.AuditEvent} 와 동일):
 * <pre>
 * securityLogger.write(
 *   SecurityEvent.of(SecurityEventType.UPLOAD_BLOCK)
 *     .withDetail("{\"filename\":\"..." + JsonUtils.escape(name) + "\",\"reason\":\"DOUBLE_EXTENSION\"}")
 * );
 * </pre>
 *
 * <p>{@link #writeAsync(SecurityLog)} 는 self-invocation 회피용으로 interface 에 노출 —
 * 외부 도메인 코드는 직접 호출 X. {@link #write(SecurityEvent)} 만 사용. (§0.46 M3 패턴)
 */
public interface SecurityLogger {

    void write(SecurityEvent event);

    /**
     * <b>내부용 — self-invocation 회피.</b> {@code SecurityLoggerImpl.write()} 가
     * {@code @Lazy SecurityLogger self} 프록시를 통해 호출. 외부에서 직접 호출 금지.
     *
     * <p>{@code @Async} + {@code @Transactional(REQUIRES_NEW)} 가 적용되어
     * 호출자 트랜잭션과 분리된 별도 스레드 + 트랜잭션에서 실행.
     */
    void writeAsync(SecurityLog row);
}
