package com.gonet.scheduler;

import com.gonet.primary.member.dormant.service.DormantService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 회원 휴면 라이프사이클 스케줄러 — 기본 매일 01:00 (Asia/Seoul).
 *
 * <p>이 배치는 <b>되돌릴 수 없다</b>. 회원을 {@code tb_member} 밖으로 옮기고 만료분을
 * 파기한다. 그래서 다른 파괴적 배치(보존 정리·탈퇴 파기)와 같은 3단 안전장치를 둔다:
 * <ul>
 *   <li>{@code enabled} — 아예 끌 수 있다. dev/local 에서 유용하다</li>
 *   <li>{@code dry-run} — 대상 건수만 로그로 남기고 아무것도 바꾸지 않는다.
 *       <b>도입 직후 기본값은 dry-run</b> 으로 두고, 매일 01:00 로그의 건수가 예상치와
 *       맞는지 1~2주 확인한 뒤 끈다</li>
 *   <li>{@code cron} 외부화 — 시각을 바꾸는 데 배포가 필요하지 않다</li>
 * </ul>
 *
 * <p>001 은 cron 이 {@code "0 0 1 * * *"} 로 <b>하드코딩</b>돼 있었고 dry-run 도 없었다.
 * CLAUDE.md 규약("cron 은 {@code ${…:기본값}} 으로 외부화하고 dry-run 플래그를 둔다")
 * 위반이라 이식하면서 교정했다.
 *
 * <p>{@code @SchedulerLock} 은 다중 인스턴스에서 중복 실행을 막는다. 락은 logging DB 의
 * {@code shedlock} 테이블에 잡힌다.
 */
@Component
public class DormantScheduler {

    private static final Logger log = LoggerFactory.getLogger(DormantScheduler.class);

    private final DormantService service;

    @Value("${gopcms.member.dormant.enabled:true}")
    private boolean enabled;

    /** true 면 대상 건수만 센다. 운영 도입 초기에는 true 로 시작할 것. */
    @Value("${gopcms.member.dormant.dry-run:false}")
    private boolean dryRun;

    public DormantScheduler(DormantService service) {
        this.service = service;
    }

    @Scheduled(cron = "${gopcms.member.dormant.cron:0 0 1 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "DormantLifecycle", lockAtLeastFor = "PT1M", lockAtMostFor = "PT1H")
    public void runDaily() {
        if (!enabled) {
            log.info("DORMANT_SCHEDULER skipped (disabled)");
            return;
        }

        long t0 = System.currentTimeMillis();
        log.info("===DORMANT_SCHEDULER start dryRun={}", dryRun);
        try {
            if (dryRun) {
                // 실행 경로와 같은 매퍼 질의를 쓴다 — 별도 카운트 쿼리를 만들면 조건이
                // 갈려서 "미리보기 0건, 실제 200명" 이 된다.
                service.previewDaily();
            } else {
                service.runDaily();
            }
        } catch (Exception ex) {
            log.error("DORMANT_SCHEDULER error: {}", ex.getMessage(), ex);
        } finally {
            log.info("===DORMANT_SCHEDULER end dryRun={} elapsedMs={}", dryRun, System.currentTimeMillis() - t0);
        }
    }
}
