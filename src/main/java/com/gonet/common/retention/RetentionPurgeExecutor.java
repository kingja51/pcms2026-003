package com.gonet.common.retention;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 도메인 단위 retention 실행 collaborator.
 *
 * <p>스케줄러가 같은 클래스에서 메서드를 호출하면 Spring AOP self-invocation 한계로
 * {@code @Transactional} 이 적용되지 않으므로, 본 별도 빈으로 분리한다.
 * 한 도메인 실패가 다른 도메인 정리를 막지 않도록 {@link Propagation#REQUIRES_NEW} 사용.
 */
@Component
public class RetentionPurgeExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeExecutor.class);

    private final AuditLogger auditLogger;

    public RetentionPurgeExecutor(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * 한 도메인의 hard delete + 감사 이벤트를 단일 트랜잭션으로 묶는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int purgeOne(RetentionTarget target, RetentionBucket bucket, LocalDateTime cutoff) {
        int n = target.purge(cutoff);
        log.info("===RETENTION_PURGE_OK table={} bucket={} cutoff={} purged={}",
            target.tableName(), bucket, cutoff, n);
        if (n > 0) {
            auditLogger.write(AuditEvent.of("RETENTION_PURGE", target.tableName())
                .withAfter("{\"bucket\":\"" + bucket.name() + "\",\"cutoff\":\""
                    + cutoff + "\",\"purged\":" + n + "}")
                .withResult("SUCCESS"));
        }
        return n;
    }

    /** dry-run — 후보 행 수만 카운트, DELETE 없음. 가벼운 read-only 트랜잭션. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int countOne(RetentionTarget target, RetentionBucket bucket, LocalDateTime cutoff) {
        int n = target.countCandidates(cutoff);
        log.info("===RETENTION_DRY_RUN table={} bucket={} cutoff={} candidates={}",
            target.tableName(), bucket, cutoff, n);
        return n;
    }
}
