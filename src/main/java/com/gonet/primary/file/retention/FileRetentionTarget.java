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
 * <p><b>파일 정리의 유일한 자동 트리거다</b>(2026-08-01 통일).
 *
 * <p>이전에는 {@code FilePurgeScheduler} 가 04:00 에, 이 target 이 04:30 에 각각
 * 같은 정리를 돌렸다. 문제는 중복 실행이 아니라 <b>cutoff 와 중단 스위치가 갈렸다</b>는
 * 점이다 — 04:00 은 {@code file.purge.retention-days}(180일) 로, 04:30 은
 * {@code retention.buckets.tb_file} 로 잘랐고, {@code retention.dry-run=true} 로 꺼도
 * 04:00 실행은 막히지 않았다. "껐는데 파일이 지워졌다" 가 가능한 구성이었다.
 *
 * <p>그래서 스케줄러를 없애고 여기로 모았다. 이제 cutoff 는 bucket 단일 출처이고,
 * 중단은 {@code gopcms.retention.enabled}(전체) 또는 {@code gopcms.retention.dry-run}
 * 하나로 통제된다. {@code gopcms.file.purge.dry-run} 도 여전히 유효하다 —
 * {@link FilePurgeService#runForCutoff} 안쪽에서 실제 삭제를 막는다(둘 중 하나만
 * true 여도 지워지지 않는 fail-closed 구성).
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
