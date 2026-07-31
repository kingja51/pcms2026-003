package com.gonet.scheduler;

import com.gonet.logging.access.service.AccessStatBatchService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * log_access → stat_* 일별 집계 스케줄러.
 *
 * <p>두 cron:
 * <ul>
 *   <li>{@code gopcms.access-stat.cron} (기본 매일 02:00) — 어제 분 정식 집계</li>
 *   <li>{@code gopcms.access-stat.live-cron} (기본 매 10분) — 오늘 분 실시간 재집계</li>
 * </ul>
 *
 * <p>실제 집계 로직은 {@link AccessStatBatchService#aggregateFor(LocalDate)} —
 * 본 스케줄러는 cron 트리거와 에러 폴백만 담당.
 */
@Component
public class AccessStatScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccessStatScheduler.class);

    private final AccessStatBatchService service;

    public AccessStatScheduler(AccessStatBatchService service) {
        this.service = service;
    }

    /** 매일 02:00 — 어제(now - 1d) 분 집계 (안정 데이터, 한 번만). */
    @Scheduled(cron = "${gopcms.access-stat.cron:0 0 2 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "AccessStatYesterday", lockAtLeastFor = "PT1M", lockAtMostFor = "PT1H")
    public void aggregateYesterday() {
        log.info("===AccessStatScheduler.aggregateYesterday started.");
        long t0 = System.currentTimeMillis();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            service.aggregateFor(yesterday);
        } catch (Exception ex) {
            log.warn("ACCESS_STAT_BATCH_FAIL date={} reason={}", yesterday, ex.getMessage());
        } finally {
            log.info("===AccessStatScheduler.aggregateYesterday ended. elapsedMs={}", System.currentTimeMillis() - t0);
        }
    }

    /**
     * 매 10분 — 오늘 분 재집계 (실시간 반영). aggregateFor 가 멱등이라 안전.
     */
    @Scheduled(cron = "${gopcms.access-stat.live-cron:0 */10 * * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "AccessStatTodayLive", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void aggregateTodayLive() {
        log.info("===AccessStatScheduler.aggregateTodayLive started.");
        long t0 = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        try {
            service.aggregateFor(today);
        } catch (Exception ex) {
            // 실시간 집계 실패는 다음 10분 cycle 에서 자동 복구
            log.warn("ACCESS_STAT_LIVE_FAIL date={} reason={}", today, ex.getMessage());
        } finally {
            log.info("===AccessStatScheduler.aggregateTodayLive ended. elapsedMs={}", System.currentTimeMillis() - t0);
        }
    }
}
