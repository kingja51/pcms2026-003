package com.gonet.logging.access.service;

import com.gonet.common.audit.AuditContext;
import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.access.dto.AccessLog;
import com.gonet.logging.access.mapper.AccessLogMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * {@link AccessLogger} 구현 — log_access INSERT.
 *
 * <p>트랜잭션 정책:
 * <ul>
 *   <li>{@link Propagation#REQUIRES_NEW} — 호출자(primary TX) 와 분리, 응답이 롤백되어도 이력 보존</li>
 *   <li>{@link LoggingDataSourceConfig#TRANSACTION_MGR} — logging DB 의 TX 매니저 명시</li>
 *   <li>{@code @Async("accessLogExecutor")} — 응답 commit 직후 백그라운드 INSERT</li>
 * </ul>
 *
 * <p>본 메서드는 {@link AccessLogSnapshot} 만 받으므로 워커 스레드에서 원본 request
 * 객체에 접근하지 않는다 — Tomcat 의 request facade recycle 과 무관하게 안전.
 *
 * <p>actor 정보(actorUserId / actorUserType / actorLoginId) 는
 * {@link AccessLogSnapshot#capture} 시점에 이미 결정되어 record 에 들어 있다.
 * 워커 스레드에서는 {@code SecurityContextHolder} 를 더 이상 조회하지 않는다 —
 * Spring Security 가 응답 직후 컨텍스트를 clear 한 뒤이므로 신뢰할 수 없기 때문.
 * (관련 회귀: 2026-04-30 모든 행이 actor_user_type=ANONYMOUS 로 기록되던 버그 해소.)
 *
 * <p>MDC 는 {@code AccessLogAsyncConfig.mdcTaskDecorator} 가 워커 스레드로 propagate
 * 하므로 trace_id / 워커 로그 상관관계는 그대로 유지.
 *
 * <p>실패는 로그만 남기고 삼킨다 — 액세스 로그 INSERT 실패가 사용자 응답을 막아선 안 됨.
 */
@Service
public class AccessLoggerImpl extends EgovAbstractServiceImpl implements AccessLogger {

    private static final Logger log = LoggerFactory.getLogger(AccessLoggerImpl.class);

    private final AccessLogMapper mapper;

    public AccessLoggerImpl(AccessLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Async("accessLogExecutor")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW,
        transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
    public void log(AccessLogSnapshot snap) {
        if (snap == null) return;
        try {
            AccessLog row = new AccessLog();
            populateFromSnapshot(row, snap);
            populateActor(row, snap);
            row.setLoggedAt(LocalDateTime.now());
            stampAuditColumns(row);
            mapper.insert(row);
        } catch (Exception ex) {
            // 격리: 액세스 로그 INSERT 실패가 사용자 응답을 막아선 안 됨
            log.warn("ACCESS_LOG_FAIL uri={} reason={}",
                snap.requestUri(), ex.getMessage());
        }
    }

    /**
     * 감사컬럼 자동 세팅 — created_by / created_ip / created_at 항상 채운다.
     *
     * <p>{@code log_access} 의 actor_user_id / client_ip 와 값이 중복되더라도
     * 일관성을 위해 항상 채운다. log_* 테이블은 INSERT only 정책이라
     * updated_xxx 컬럼은 DROP 됨 (2026-04-27).
     */
    private static void stampAuditColumns(AccessLog row) {
        String by  = (row.getActorUserId() != null && !row.getActorUserId().isBlank())
                     ? row.getActorUserId()
                     : AuditContext.ANONYMOUS;
        String ip  = row.getClientIp();
        LocalDateTime now = (row.getLoggedAt() != null) ? row.getLoggedAt() : LocalDateTime.now();
        row.setCreatedBy(by);
        row.setCreatedIp(ip);
        row.setCreatedAt(now);
    }

    private static void populateFromSnapshot(AccessLog row, AccessLogSnapshot s) {
        row.setRequestUri(s.requestUri());
        row.setHttpMethod(s.httpMethod());
        row.setQueryString(s.queryString());
        row.setReferer(s.referer());
        row.setClientIp(s.clientIp());
        row.setUserAgent(s.userAgent());
        row.setSessionId(s.sessionIdTail());
        row.setTraceId(s.traceId());
        row.setSiteId(s.siteId());
        row.setSiteCode(s.siteCode());
        row.setStatusCode(s.statusCode());
        row.setResponseMs(s.responseMs());
        row.setBytesSent(s.bytesSent());
    }

    /**
     * actor 정보는 스냅샷에서 가져온다 — 워커 스레드에서는 SecurityContext 가 비어 있으므로
     * 캡처 시점({@link AccessLogSnapshot#capture}) 에 결정된 값을 사용해야 한다.
     */
    private static void populateActor(AccessLog row, AccessLogSnapshot s) {
        row.setActorUserId(s.actorUserId());
        row.setActorUserType(
            (s.actorUserType() == null || s.actorUserType().isBlank())
                ? AuditContext.ANONYMOUS
                : s.actorUserType());
        row.setActorLoginId(s.actorLoginId());
    }
}
