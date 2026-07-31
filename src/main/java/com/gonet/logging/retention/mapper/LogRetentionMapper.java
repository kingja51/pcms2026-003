package com.gonet.logging.retention.mapper;

import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * logging DB 의 log_* 테이블 보존 정책 정리 Mapper.
 *
 * <p>{@link com.gonet.scheduler.LogRetentionScheduler} 가
 * 매일 새벽 호출하여 cutoff 이전 행을 batch DELETE.
 *
 * <p>각 테이블의 시간 컬럼이 다르므로 메서드를 분리:
 * <ul>
 *   <li>log_access        / logged_at</li>
 *   <li>log_audit         / logged_at</li>
 *   <li>log_login         / logged_at</li>
 *   <li>log_file_download / downloaded_at</li>
 * </ul>
 *
 * <p>대용량 DELETE 시 트랜잭션이 너무 커지지 않도록 LIMIT 으로 batch 분할 — 호출 측에서 반복.
 */
@EgovMapper
public interface LogRetentionMapper {

    int deleteLogAccessOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                  @Param("batchSize") int batchSize);

    int deleteLogAuditOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                 @Param("batchSize") int batchSize);

    int deleteLogLoginOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                 @Param("batchSize") int batchSize);

    int deleteLogFileDownloadOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                        @Param("batchSize") int batchSize);

    int deleteLogErrorOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                 @Param("batchSize") int batchSize);

    int deleteLogPrivacyAccessOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                         @Param("batchSize") int batchSize);

    // ── dry-run 미리보기 — delete 와 같은 술어, batchSize 없음 ──────────────

    int countLogAccessOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countLogAuditOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countLogLoginOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countLogFileDownloadOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countLogErrorOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countLogPrivacyAccessOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
