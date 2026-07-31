package com.gonet.scheduler;

import com.gonet.primary.file.config.FilePurgeProperties;
import com.gonet.primary.file.service.FilePurgeService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 파일 물리 삭제 스케줄러 — 기본 매일 04:00 (Asia/Seoul).
 *
 * <p>{@code @Scheduled(cron = "${gopcms.file.purge.cron:0 0 4 * * *}")} 로 application.yml
 * 의 {@code gopcms.file.purge.cron} 값을 런타임 바인딩. 변경 시 재기동 필요.
 *
 * <p>{@link org.springframework.scheduling.annotation.EnableScheduling @EnableScheduling} 은
 * {@code GopcmsApplication} 에 이미 활성. ShedLock 은 {@code ShedLockConfig} 에서 활성.
 *
 * <p><b>분산 락</b>: {@code @SchedulerLock("FilePurge")} 로 다중 인스턴스 환경에서 동일
 * 시각 중복 실행 차단. lockAtMostFor=PT2H — 대량 파일 삭제 시 lock 만료 회피.
 */
@Component
public class FilePurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(FilePurgeScheduler.class);

    private final FilePurgeService    service;
    private final FilePurgeProperties properties;

    public FilePurgeScheduler(FilePurgeService service, FilePurgeProperties properties) {
        this.service    = service;
        this.properties = properties;
    }

    @Scheduled(cron = "${gopcms.file.purge.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "FilePurge", lockAtLeastFor = "PT1M", lockAtMostFor = "PT2H")
    public void runDaily() {
        if (!properties.isEnabled()) {
            log.info("FILE_PURGE_SCHEDULER skipped (disabled)");
            return;
        }
        long t0 = System.currentTimeMillis();
        log.info("===FILE_PURGE_SCHEDULER start");
        try {
            FilePurgeService.PurgeResult r = service.runOnce();
            log.info("===FILE_PURGE_SCHEDULER completion ok={} fail={} bytes={}",
                r.ok(), r.fail(), r.totalBytes());
        } catch (Exception ex) {
            log.error("FILE_PURGE_SCHEDULER error: {}", ex.getMessage(), ex);
        } finally {
            log.info("===FILE_PURGE_SCHEDULER end elapsedMs={}", System.currentTimeMillis() - t0);
        }
    }
}
