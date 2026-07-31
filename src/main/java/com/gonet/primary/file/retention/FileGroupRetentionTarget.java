package com.gonet.primary.file.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.file.mapper.FileGroupMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_file_group soft-delete retention target.
 *
 * <p>FK 종속성 — file_group 은 file(15) 의 부모. childOrder=20 으로 자식 정리 후 실행.
 * <p>bucket = DAYS_180 — file(DAYS_180) 과 동일. 보안 감사 창구 정책 정합 (웹쉘 침해 흔적
 * 추적 + 그룹 메타데이터 함께 보존). §0.36 F 첫 번째 항목 (의도적 보류였음) 해소.
 */
@Component
public class FileGroupRetentionTarget implements RetentionTarget {

    private final FileGroupMapper mapper;

    public FileGroupRetentionTarget(FileGroupMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_file_group"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_180; }
    @Override public int childOrder()                { return 20; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
