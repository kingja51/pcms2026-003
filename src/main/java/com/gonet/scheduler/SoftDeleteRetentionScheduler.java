package com.gonet.scheduler;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionProperties;
import com.gonet.common.retention.RetentionPurgeExecutor;
import com.gonet.common.retention.RetentionTarget;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Soft-delete retention 스케줄러 — {@code delete_yn='Y'} 행을 bucket 별 보존 기간 후 hard delete.
 *
 * <p>cron 시각: 매일 04:30 (LogRetention 03:30, FilePurge 04:00 사이의 빈 시간대).
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>모든 {@link RetentionTarget} 빈을 {@code childOrder} 오름차순으로 정렬 (자식 → 부모)</li>
 *   <li>도메인별로 bucket 결정 — application.yml {@code gopcms.retention.buckets[tableName]} 가
 *       있으면 그 값, 없으면 {@link RetentionTarget#defaultBucket()}</li>
 *   <li>cutoff = now - bucket.days() 계산 후 {@link RetentionPurgeExecutor} 위임</li>
 *   <li>도메인 단위 새 트랜잭션(REQUIRES_NEW) — 한 도메인 실패가 다른 도메인 정리를 막지 않음</li>
 * </ol>
 *
 * <p>dry-run — {@code gopcms.retention.dry-run=true} 면 {@code countCandidates} 만 호출.
 * 도입 첫 1~2주 안전 검증 후 false 전환 권고.
 */
@Component
@EnableConfigurationProperties(RetentionProperties.class)
public class SoftDeleteRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteRetentionScheduler.class);

    private final List<RetentionTarget> targets;
    private final RetentionProperties props;
    private final RetentionPurgeExecutor executor;

    public SoftDeleteRetentionScheduler(List<RetentionTarget> targets,
                                         RetentionProperties props,
                                         RetentionPurgeExecutor executor) {
        // childOrder ASC — 자식 도메인이 부모보다 먼저 실행
        this.targets = targets.stream()
            .sorted(Comparator.comparingInt(RetentionTarget::childOrder))
            .toList();
        this.props = props;
        this.executor = executor;
    }

    @Scheduled(cron = "${gopcms.retention.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "SoftDeleteRetention", lockAtLeastFor = "PT1M", lockAtMostFor = "PT2H")
    public void purge() {
        log.info("===SoftDeleteRetentionScheduler.purge started.");
        long t0 = System.currentTimeMillis();
        try {
        if (!props.isEnabled()) {
            log.info("RETENTION_SKIP enabled=false");
            return;
        }
        if (targets.isEmpty()) {
            log.info("===RETENTION_NO_TARGETS — RetentionTarget 빈 미등록");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("===RETENTION_BEGIN dryRun={} targets={} now={}", props.isDryRun(), targets.size(), now);

        int total = 0;
        for (RetentionTarget t : targets) {
            try {
                RetentionBucket bucket = resolveBucket(t);
                LocalDateTime cutoff = bucket.cutoff(now);
                int n = props.isDryRun()
                    ? executor.countOne(t, bucket, cutoff)
                    : executor.purgeOne(t, bucket, cutoff);
                total += n;
            } catch (Exception ex) {
                // 한 도메인 실패가 다른 도메인 정리를 막지 않도록 catch
                log.warn("RETENTION_DOMAIN_FAIL table={} reason={}", t.tableName(), ex.getMessage(), ex);
            }
        }

        log.info("===RETENTION_END dryRun={} total={}", props.isDryRun(), total);
        } finally {
            log.info("===SoftDeleteRetentionScheduler.purge ended. elapsedMs={}", System.currentTimeMillis() - t0);
        }
    }

    /** application.yml override 우선, 없으면 target 의 defaultBucket. */
    private RetentionBucket resolveBucket(RetentionTarget t) {
        RetentionBucket override = props.getBuckets().get(t.tableName());
        return override != null ? override : t.defaultBucket();
    }
}
