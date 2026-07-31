package com.gonet.scheduler;

import com.gonet.common.lifecycle.WithdrawPurgeExecutor;
import com.gonet.common.lifecycle.WithdrawPurgeProperties;
import com.gonet.common.lifecycle.WithdrawPurgeTarget;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 탈퇴(withdraw) 만료 영구 삭제 스케줄러 — 매일 04:45 (Asia/Seoul).
 *
 * <p>cron 시각 — DormantScheduler 01:00 / LogRetention 03:30 / FilePurge 04:00 /
 * SoftDeleteRetention 04:30 다음의 빈 시간대.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>모든 {@link WithdrawPurgeTarget} 빈 자동 수집 (ordering 무관 — 도메인 간 FK 없음)</li>
 *   <li>{@code retention_expire_at &lt;= now} 인 행을 도메인별 hard delete</li>
 *   <li>도메인 단위 새 트랜잭션(REQUIRES_NEW) — 한 도메인 실패가 다른 도메인 정리를 막지 않음</li>
 *   <li>{@code WITHDRAW_PURGE} 감사 이벤트 발행</li>
 * </ol>
 *
 * <p>dry-run — {@code gopcms.lifecycle.withdraw-purge.dry-run=true} 면 countExpired 만 호출.
 * 도입 첫 1~2주 안전 검증 후 false 전환 권고.
 *
 * <p>보존 정책 — retention_expire_at 컬럼은 등록(탈퇴 처리) 시점에 부여:
 * <ul>
 *   <li>회원: 탈퇴 후 1년 (PIPA §29 기본)</li>
 *   <li>관리자: 탈퇴 후 1년 (GoPCMS 운영 컨텍스트 — 실제 퇴사·인사 데이터는 MIS 가 보유)</li>
 *   <li>직원: 탈퇴 후 1년 (실제 노동법 보존 의무는 MIS 가 담당)</li>
 * </ul>
 * 본 스케줄러는 retention_expire_at 만 보고 만료된 행만 정리하므로, 운영 정책이 바뀌면
 * 등록 시점 코드만 조정하고 인프라는 그대로 동작 — 도메인별/사이트별 차별 정책도 가능.
 */
@Component
@EnableConfigurationProperties(WithdrawPurgeProperties.class)
public class WithdrawPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(WithdrawPurgeScheduler.class);

    private final List<WithdrawPurgeTarget> targets;
    private final WithdrawPurgeProperties props;
    private final WithdrawPurgeExecutor executor;

    public WithdrawPurgeScheduler(List<WithdrawPurgeTarget> targets,
                                    WithdrawPurgeProperties props,
                                    WithdrawPurgeExecutor executor) {
        this.targets = targets;
        this.props = props;
        this.executor = executor;
    }

    @Scheduled(cron = "${gopcms.lifecycle.withdraw-purge.cron:0 45 4 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "WithdrawPurge", lockAtLeastFor = "PT1M", lockAtMostFor = "PT2H")
    public void purge() {
        log.info("===WithdrawPurgeScheduler.purge started.");
        long t0 = System.currentTimeMillis();
        try {
        if (!props.isEnabled()) {
            log.info("WITHDRAW_PURGE_SKIP enabled=false");
            return;
        }
        if (targets.isEmpty()) {
            log.info("===WITHDRAW_PURGE_NO_TARGETS — WithdrawPurgeTarget 빈 미등록");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("===WITHDRAW_PURGE_BEGIN dryRun={} targets={} now={}",
            props.isDryRun(), targets.size(), now);

        int total = 0;
        for (WithdrawPurgeTarget t : targets) {
            try {
                int n = props.isDryRun()
                    ? executor.countOne(t, now)
                    : executor.purgeOne(t, now);
                total += n;
            } catch (Exception ex) {
                log.warn("WITHDRAW_PURGE_DOMAIN_FAIL table={} reason={}",
                    t.tableName(), ex.getMessage(), ex);
            }
        }

        log.info("===WITHDRAW_PURGE_END dryRun={} total={}", props.isDryRun(), total);
        } finally {
            log.info("===WithdrawPurgeScheduler.purge ended. elapsedMs={}", System.currentTimeMillis() - t0);
        }
    }
}
