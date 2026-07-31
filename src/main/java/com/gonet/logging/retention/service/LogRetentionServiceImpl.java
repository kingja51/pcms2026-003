package com.gonet.logging.retention.service;

import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.retention.mapper.LogRetentionMapper;
import com.gonet.logging.security.mapper.SecurityLogMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.BiFunction;

/**
 * {@link LogRetentionService} 구현 — LIMIT batch DELETE 반복.
 *
 * <p>설계 포인트:
 * <ul>
 *   <li>{@code @Transactional} 부재 — HikariCP autoCommit=true 라 각 mapper 호출이
 *       자동 커밋. batch 단위 분리 커밋이 자연스럽게 보장된다.</li>
 *   <li>큰 단일 DELETE 회피 — binlog 폭증·복제 지연·lock 시간 증가 방지</li>
 *   <li>batch 사이 짧은 yield — 다른 INSERT/SELECT 와 경합 완화</li>
 *   <li>최대 batch 반복 횟수 — 무한 루프 방지 + 한 번 호출의 최대 1M 행 처리</li>
 * </ul>
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED, transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
public class LogRetentionServiceImpl extends EgovAbstractServiceImpl implements LogRetentionService {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionServiceImpl.class);

    /** batch DELETE 1회 당 최대 행 수 — 트랜잭션 크기 제한. */
    private static final int BATCH_SIZE  = 5_000;

    /** batch 반복 상한 — 한 번 호출에서 최대 5,000 × 200 = 1,000,000 행. */
    private static final int MAX_BATCHES = 200;

    /** batch 사이 sleep ms — lock 경합 완화. */
    private static final long INTER_BATCH_SLEEP_MS = 50L;

    private final LogRetentionMapper mapper;
    private final SecurityLogMapper  securityMapper;

    public LogRetentionServiceImpl(LogRetentionMapper mapper, SecurityLogMapper securityMapper) {
        this.mapper = mapper;
        this.securityMapper = securityMapper;
    }

    /**
     * dry-run 미리보기 — 조회만 한다. 삭제와 같은 술어를 쓰되 batchSize 는 적용하지 않는다.
     *
     * <p>{@code log_privacy_access} 는 보존 기간이 달라(PIPA 24개월) 별도 cutoff 를 받는다.
     * 여기서 같은 cutoff 를 쓰면 미리보기가 실제보다 많은 건수를 보고한다.
     *
     * <p>{@code log_security} 는 {@link #purgeLogSecurity} 가 별도 매퍼를 쓰므로
     * 이 미리보기에 넣지 않는다 — {@link PurgeResult} 에도 자리가 없다.
     * 스케줄러 로그에서 security 건수는 따로 확인한다.
     */
    @Override
    public PurgeResult preview(LocalDateTime cutoff, LocalDateTime privacyCutoff) {
        long access  = mapper.countLogAccessOlderThan(cutoff);
        long audit   = mapper.countLogAuditOlderThan(cutoff);
        long login   = mapper.countLogLoginOlderThan(cutoff);
        long fileDl  = mapper.countLogFileDownloadOlderThan(cutoff);
        long error   = mapper.countLogErrorOlderThan(cutoff);
        long privacy = mapper.countLogPrivacyAccessOlderThan(privacyCutoff);

        log.info("===LOG_RETENTION_DRY_RUN cutoff={} privacyCutoff={} "
                 + "access={} audit={} login={} fileDownload={} error={} privacyAccess={}",
            cutoff, privacyCutoff, access, audit, login, fileDl, error, privacy);
        return new PurgeResult(access, audit, login, fileDl, error);
    }

    @Override
    public PurgeResult purgeAll(LocalDateTime cutoff) {
        long access = purgeLogAccess(cutoff);
        long audit  = purgeLogAudit(cutoff);
        long login  = purgeLogLogin(cutoff);
        long fileDl = purgeLogFileDownload(cutoff);
        long error  = purgeLogError(cutoff);
        log.info("===LOG_RETENTION_PURGE_TOTAL cutoff={} access={} audit={} login={} fileDownload={} error={}",
            cutoff, access, audit, login, fileDl, error);
        return new PurgeResult(access, audit, login, fileDl, error);
    }

    @Override public long purgeLogAccess(LocalDateTime cutoff) {
        return loop("log_access", cutoff, mapper::deleteLogAccessOlderThan);
    }

    @Override public long purgeLogAudit(LocalDateTime cutoff) {
        return loop("log_audit", cutoff, mapper::deleteLogAuditOlderThan);
    }

    @Override public long purgeLogLogin(LocalDateTime cutoff) {
        return loop("log_login", cutoff, mapper::deleteLogLoginOlderThan);
    }

    @Override public long purgeLogFileDownload(LocalDateTime cutoff) {
        return loop("log_file_download", cutoff, mapper::deleteLogFileDownloadOlderThan);
    }

    @Override public long purgeLogError(LocalDateTime cutoff) {
        return loop("log_error", cutoff, mapper::deleteLogErrorOlderThan);
    }

    @Override public long purgeLogPrivacyAccess(LocalDateTime cutoff) {
        return loop("log_privacy_access", cutoff, mapper::deleteLogPrivacyAccessOlderThan);
    }

    @Override public long purgeLogSecurity(LocalDateTime cutoff) {
        // SecurityLogMapper.deleteOlderThan 은 SecurityLogMapper 에 정의된 batch DELETE.
        // LogRetentionMapper 가 모든 log_* 를 일관되게 다루지 않는 이유: SecurityLogMapper 가
        // 이미 viewer/insert 와 함께 단일 mapper 로 결합돼 있어 retention 만 분리하면 일관성↓.
        return loop("log_security", cutoff, securityMapper::deleteOlderThan);
    }

    /**
     * batch DELETE 반복 호출자.
     * @param deleter (cutoff, batchSize) → affectedRows
     */
    private long loop(String tableName,
                       LocalDateTime cutoff,
                       BiFunction<LocalDateTime, Integer, Integer> deleter) {
        long total   = 0;
        int  batches = 0;
        while (batches < MAX_BATCHES) {
            int affected;
            try {
                affected = deleter.apply(cutoff, BATCH_SIZE);
            } catch (Exception ex) {
                log.warn("LOG_RETENTION_BATCH_FAIL table={} cutoff={} reason={}",
                    tableName, cutoff, ex.getMessage());
                break;
            }
            total += affected;
            batches++;
            if (affected < BATCH_SIZE) break;     // 더 이상 삭제할 게 없거나 마지막 batch
            try { Thread.sleep(INTER_BATCH_SLEEP_MS); }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (total > 0) {
            log.info("===LOG_RETENTION_PURGE_OK table={} cutoff={} deleted={} batches={}",
                tableName, cutoff, total, batches);
        } else {
            log.info("LOG_RETENTION_PURGE_NOOP table={} cutoff={}", tableName, cutoff);
        }
        return total;
    }
}
