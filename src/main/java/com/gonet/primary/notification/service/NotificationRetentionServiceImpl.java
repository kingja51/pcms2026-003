package com.gonet.primary.notification.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.notification.mapper.NotificationMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class NotificationRetentionServiceImpl extends EgovAbstractServiceImpl
        implements NotificationRetentionService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionServiceImpl.class);

    private final NotificationMapper mapper;
    private final AuditLogger        auditLogger;

    @Value("${gopcms.notification.retention.read-days:90}")
    private int readDays;

    @Value("${gopcms.notification.retention.purge-days:365}")
    private int purgeDays;

    public NotificationRetentionServiceImpl(NotificationMapper mapper, AuditLogger auditLogger) {
        this.mapper      = mapper;
        this.auditLogger = auditLogger;
    }

    @Override public int readDays()  { return readDays; }
    @Override public int purgeDays() { return purgeDays; }

    @Override
    public Preview preview() {
        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime readCutoff  = now.minusDays(readDays);
        LocalDateTime purgeCutoff = now.minusDays(purgeDays);
        long oldRead    = safeCount(() -> mapper.countOldRead(readCutoff));
        long oldDeleted = safeCount(() -> mapper.countOldDeleted(purgeCutoff));
        return new Preview(readCutoff, purgeCutoff, oldRead, oldDeleted);
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public Result runNow() {
        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime readCutoff  = now.minusDays(readDays);
        LocalDateTime purgeCutoff = now.minusDays(purgeDays);
        log.info("===NOTIFICATION_RETENTION_RUN read.cutoff={} purge.cutoff={}",
            readCutoff, purgeCutoff);
        int softDeleted = 0, purged = 0;
        try {
            softDeleted = mapper.softDeleteOldRead(readCutoff);
            purged      = mapper.purgeOldDeleted(purgeCutoff);
            log.info("===NOTIFICATION_RETENTION_DONE softDeleted={} purged={}", softDeleted, purged);
            auditLogger.write(AuditEvent.of("NOTIFICATION_RETENTION_RUN", "tb_notification")
                .withResult("SUCCESS")
                .withAfter("{\"softDeleted\":" + softDeleted
                    + ",\"purged\":"           + purged
                    + ",\"readDays\":"         + readDays
                    + ",\"purgeDays\":"        + purgeDays + "}"));
        } catch (Exception ex) {
            log.error("NOTIFICATION_RETENTION_FAIL", ex);
            auditLogger.write(AuditEvent.of("NOTIFICATION_RETENTION_RUN", "tb_notification")
                .withResult("FAIL")
                .withAfter("{\"error\":\"" + ex.getClass().getSimpleName() + "\"}"));
        }
        return new Result(readCutoff, purgeCutoff, softDeleted, purged);
    }

    /** count 호출 단위 fail-safe — 권한/스키마 이슈로 실패해도 preview 화면은 0 으로 노출. */
    private long safeCount(java.util.function.LongSupplier sup) {
        try { return sup.getAsLong(); }
        catch (Exception ex) {
            log.warn("NOTIFICATION_RETENTION_COUNT_FAIL reason={}", ex.getMessage());
            return 0L;
        }
    }
}
