package com.gonet.common.lifecycle;

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
 * 도메인별 withdraw purge 실행 collaborator.
 *
 * <p>스케줄러가 같은 클래스에서 메서드를 호출하면 Spring AOP self-invocation 한계로
 * {@code @Transactional} 이 적용되지 않으므로, 본 별도 빈으로 분리한다.
 * 한 도메인 실패가 다른 도메인 정리를 막지 않도록 {@link Propagation#REQUIRES_NEW} 사용.
 */
@Component
public class WithdrawPurgeExecutor {

    private static final Logger log = LoggerFactory.getLogger(WithdrawPurgeExecutor.class);

    private final AuditLogger auditLogger;

    public WithdrawPurgeExecutor(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * 한 도메인의 만료 withdraw 행 hard delete + 감사 이벤트를 단일 트랜잭션으로 묶는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int purgeOne(WithdrawPurgeTarget target, LocalDateTime now) {
        int n = target.purgeExpired(now);
        log.info("===WITHDRAW_PURGE_OK table={} now={} purged={}", target.tableName(), now, n);
        if (n > 0) {
            auditLogger.write(AuditEvent.of("WITHDRAW_PURGE", target.tableName())
                .withAfter("{\"now\":\"" + now + "\",\"purged\":" + n + "}")
                .withResult("SUCCESS"));
        }
        return n;
    }

    /** dry-run — 만료 후보 행 수만 카운트. read-only. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int countOne(WithdrawPurgeTarget target, LocalDateTime now) {
        int n = target.countExpired(now);
        log.info("===WITHDRAW_PURGE_DRY_RUN table={} now={} candidates={}",
            target.tableName(), now, n);
        return n;
    }
}
