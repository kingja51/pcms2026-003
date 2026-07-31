package com.gonet.common.lifecycle;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 탈퇴(withdraw) 만료 영구 삭제 스케줄러 설정.
 *
 * <pre>
 * gopcms.lifecycle.withdraw-purge:
 *   enabled: true
 *   cron: "0 45 4 * * *"       # 매일 04:45 (Retention 04:30 직후)
 *   dry-run: true               # 도입 첫 1~2주 권장 — countExpired 만 로그
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.lifecycle.withdraw-purge")
public class WithdrawPurgeProperties {

    /** 전체 활성 여부. false 면 스케줄러 진입 시 즉시 return. */
    private boolean enabled = true;

    /**
     * dry-run 모드. true 면 도메인별 countExpired 만 호출하고 실제 purge 는 skip.
     * 카운트 결과는 동일 로그 라벨로 출력되어 운영 리포트로 활용 가능.
     */
    private boolean dryRun = true;
}
