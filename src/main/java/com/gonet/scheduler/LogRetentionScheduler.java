package com.gonet.scheduler;

import com.gonet.logging.retention.service.LogRetentionService;
import com.gonet.logging.retention.service.LogRetentionService.PurgeResult;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * logging DB 의 log_* 테이블 6개월 retention 정책 스케줄러.
 *
 * <p>대상 테이블:
 * <ul>
 *   <li>{@code log_access}        / {@code logged_at}</li>
 *   <li>{@code log_audit}         / {@code logged_at}</li>
 *   <li>{@code log_login}         / {@code logged_at}</li>
 *   <li>{@code log_file_download} / {@code downloaded_at}</li>
 * </ul>
 *
 * <p>cutoff = now - {@code gopcms.log.retention.months} (기본 6개월).
 *
 * <p>실행 시각: 매일 새벽 03:30 — 02:00 stat 집계 배치 이후, 04:00 일반 백업 이전.
 * 동시 실행 가능성 있는 다른 무거운 배치와 시간대 분산.
 */
@Component
public class LogRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionScheduler.class);

    private final LogRetentionService service;

    /**
     * true 면 대상 건수만 로그로 남기고 아무것도 지우지 않는다.
     * 도입 초기에는 true 로 두고 건수를 확인한 뒤 끈다.
     */
    @Value("${gopcms.log.retention.dry-run:false}")
    private boolean dryRun;

    @Value("${gopcms.log.retention.months:12}")
    private int retentionMonths;

    /**
     * log_privacy_access 보존 기간 — PIPA §29 + 안전성확보조치 고시 §8.
     * 일반 1년, 5만명 이상 정보주체 / 고유식별정보·민감정보 처리 시 2년.
     * 안전 측 24개월 적용.
     */
    @Value("${gopcms.log.privacy-access.retention-months:24}")
    private int privacyAccessRetentionMonths;

    /**
     * log_security 보존 기간 — 일반 log_* 와 동일 6개월 기본.
     * 운영 정책상 보안 이벤트만 별도 보존 (예: 24개월) 이 필요하면 설정에서 override.
     */
    @Value("${gopcms.log.security.retention-months:6}")
    private int securityRetentionMonths;

    public LogRetentionScheduler(LogRetentionService service) {
        this.service = service;
    }

    /** 매일 03:30 — 일반 log_* 5종 + log_privacy_access (별도 cutoff). */
    @Scheduled(cron = "${gopcms.log.retention.cron:0 30 3 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "LogRetention", lockAtLeastFor = "PT1M", lockAtMostFor = "PT2H")
    public void purgeOldLogs() {
        log.info("===LogRetentionScheduler.purgeOldLogs started. dryRun={}", dryRun);
        long t0 = System.currentTimeMillis();
        try {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(retentionMonths);

        // dry-run — 아무것도 지우지 않고 대상 건수만 남긴다.
        //
        // 로그 삭제는 되돌릴 수 없고, 감사·접속 로그는 사고가 난 뒤에야 필요해진다.
        // retention-months 를 잘못 넣어 의도보다 훨씬 많이 지우는 실수는 지우고 나서는
        // 확인할 방법이 없다. 도입 초기에는 dry-run 으로 건수를 보고 켠다.
        if (dryRun) {
            LocalDateTime pii = LocalDateTime.now().minusMonths(privacyAccessRetentionMonths);
            service.preview(cutoff, pii);
            return;
        }

        log.info("===LOG_RETENTION_BEGIN cutoff={} ({}개월 이전 데이터 삭제)",
            cutoff, retentionMonths);
        try {
            PurgeResult r = service.purgeAll(cutoff);
            log.info("===LOG_RETENTION_END total={} access={} audit={} login={} fileDownload={} error={}",
                r.total(), r.access(), r.audit(), r.login(), r.fileDownload(), r.error());
        } catch (Exception ex) {
            log.error("LOG_RETENTION_FAIL cutoff={}", cutoff, ex);
        }

        // PIPA: log_privacy_access 는 24개월 별도 cutoff
        LocalDateTime piiCutoff = LocalDateTime.now().minusMonths(privacyAccessRetentionMonths);
        log.info("===PRIVACY_RETENTION_BEGIN cutoff={} ({}개월)",
            piiCutoff, privacyAccessRetentionMonths);
        try {
            long deleted = service.purgeLogPrivacyAccess(piiCutoff);
            log.info("===PRIVACY_RETENTION_END deleted={}", deleted);
        } catch (Exception ex) {
            log.error("PRIVACY_RETENTION_FAIL cutoff={}", piiCutoff, ex);
        }

        // log_security — 보안 이벤트 분리 적재 (§0.48). 기본 일반 cutoff 와 동일하지만
        // 정책 분기 가능성을 고려해 별도 호출.
        LocalDateTime secCutoff = LocalDateTime.now().minusMonths(securityRetentionMonths);
        log.info("===SECURITY_RETENTION_BEGIN cutoff={} ({}개월)",
            secCutoff, securityRetentionMonths);
        try {
            long deleted = service.purgeLogSecurity(secCutoff);
            log.info("===SECURITY_RETENTION_END deleted={}", deleted);
        } catch (Exception ex) {
            log.error("SECURITY_RETENTION_FAIL cutoff={}", secCutoff, ex);
        }
        } finally {
            log.info("===LogRetentionScheduler.purgeOldLogs ended. elapsedMs={}", System.currentTimeMillis() - t0);
        }
    }
}
