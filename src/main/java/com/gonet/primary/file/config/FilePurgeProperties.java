package com.gonet.primary.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 파일 물리 삭제 배치 설정 — {@code gopcms.file.purge.*}.
 *
 * <p>소프트 삭제({@code tb_file.delete_yn='Y'}) 된 파일이 {@code retention-days}
 * 이상 경과하면 물리 저장소(uploads/thumbnail/quarantine) 와 DB 행을 hard delete.
 * 유예 기간은 "문제 있는 파일을 숨기려고 삭제한 조작" 을 감지할 감사 창구 확보용.
 */
@Component
@ConfigurationProperties(prefix = "gopcms.file.purge")
@Getter
@Setter
public class FilePurgeProperties {

    /** 배치 활성 여부 — false 면 스케줄러 자체가 run 하지 않음. */
    private boolean enabled = true;

    /** cron 식 (zone=Asia/Seoul). 기본 매일 04:00. */
    private String cron = "0 0 4 * * *";

    /** 보존 일수 — 이 일수 이상 경과한 soft-deleted 파일이 purge 대상. 기본 180(6개월). */
    private int retentionDays = 180;

    /** 1회 실행당 최대 처리 건수. 대량 삭제로 인한 DB 부하 완화. */
    private int batchSize = 500;

    /** true 면 로그만 남기고 실제 삭제는 skip — 초기 검증/재해 복구용. */
    private boolean dryRun = false;
}
