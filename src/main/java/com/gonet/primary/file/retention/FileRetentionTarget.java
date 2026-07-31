package com.gonet.primary.file.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.file.mapper.FileMapper;
import com.gonet.primary.file.service.FilePurgeService;
import com.gonet.primary.file.service.FilePurgeService.PurgeResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_file soft-delete retention target.
 *
 * <p>FK 종속성 — 파일은 {@code tb_file_group} 의 자식. 부모 그룹 retention 은
 * 미도입이라 file 만 정리해도 FK 위반 없음. 향후 group 도 retention 도입 시
 * file 이 먼저 정리되도록 file=15, group=25 정도로 ordering.
 *
 * <p>디스크 통합 정리 — DB row 만 hard delete 하면 디스크의 upload/quarantine/thumbnail
 * 파일이 orphan 으로 남는다. 본 target 은 {@link FilePurgeService#runForCutoff} 로 위임하여
 * 디스크 + DB 동시 정리. 같은 cutoff 를 두 번 만나도 두 번째는 candidates 가 0 이라 무해.
 *
 * <p>cutoff 정책 — RetentionScheduler 의 bucket(예: DAYS_30) 이 단일 출처. 기존
 * {@code gopcms.file.purge.retention-days} 는 {@link com.gonet.scheduler.FilePurgeScheduler}
 * 의 자체 cron 에서만 사용. 정책 일원화를 위해 운영에서 FilePurgeScheduler 를 비활성하고
 * retention 으로 통합 운영 권고 (CLAUDE.md §0.36 운영 가이드).
 */
@Component
public class FileRetentionTarget implements RetentionTarget {

    private final FilePurgeService filePurgeService;
    private final FileMapper       mapper;

    public FileRetentionTarget(FilePurgeService filePurgeService, FileMapper mapper) {
        this.filePurgeService = filePurgeService;
        this.mapper           = mapper;
    }

    @Override
    public String tableName() {
        return "tb_file";
    }

    @Override
    public RetentionBucket defaultBucket() {
        return RetentionBucket.DAYS_30;
    }

    @Override
    public int childOrder() {
        return 15;
    }

    @Override
    public int purge(LocalDateTime cutoff) {
        // 디스크(upload/quarantine/thumbnail) + DB row 통합 정리.
        // 개별 파일 트랜잭션은 FilePurgeService.purgeOne(REQUIRES_NEW)이 처리.
        PurgeResult r = filePurgeService.runForCutoff(cutoff);
        return r.ok();
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        // dry-run 카운트 — 디스크 접근 없이 DB 후보 수만.
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
