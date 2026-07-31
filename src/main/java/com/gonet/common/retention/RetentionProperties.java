package com.gonet.common.retention;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Soft-delete retention 스케줄러 설정.
 *
 * <pre>
 * gopcms.retention:
 *   enabled: true
 *   cron: "0 30 4 * * *"       # 매일 04:30 (LogRetention 03:30, FilePurge 04:00 사이)
 *   dry-run: false              # 도입 첫 1~2주 권장 true — DELETE 없이 카운트만 로그
 *   buckets:                    # 테이블 → bucket override (RetentionTarget.defaultBucket() 위에 적용)
 *     tb_bbs_article: DAYS_30
 *     tb_bbs_comment: DAYS_30
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.retention")
public class RetentionProperties {

    /** 전체 활성 여부. false 면 스케줄러 진입 시 즉시 return. */
    private boolean enabled = true;

    /**
     * dry-run 모드. true 면 도메인별 countCandidates 만 호출하고 실제 purge 는 skip.
     * 카운트 결과는 동일 로그 라벨로 출력되어 운영 리포트로 활용 가능.
     */
    private boolean dryRun = false;

    /** 구현체의 defaultBucket 을 override. 키는 {@link RetentionTarget#tableName()} 일치. */
    private Map<String, RetentionBucket> buckets = new LinkedHashMap<>();
}
