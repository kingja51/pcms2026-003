package com.gonet.logging.security.mapper;

import com.gonet.logging.security.dto.SecurityLog;
import com.gonet.logging.viewer.dto.LogSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * log_security INSERT + viewer 조회 Mapper (logging DB).
 *
 * <p>I-024 규약: 본 Mapper 는 {@code SecurityLogMapper_maria.xml} +
 * {@code SecurityLogMapper_maria.xml} 양쪽이 동거. PrimaryMyBatisConfig /
 * LoggingMyBatisConfig 가 환경별 선택.
 */
@EgovMapper
public interface SecurityLogMapper {

    void insert(SecurityLog row);

    List<SecurityLog> findRecent(@Param("search") LogSearch search);

    long countRecent(@Param("search") LogSearch search);

    SecurityLog findById(@Param("id") long id);

    /** trace_id timeline 통합 viewer 용 */
    List<SecurityLog> findByTraceId(@Param("traceId") String traceId);

    /** retention 정책 — cutoff 이전 행 batch DELETE. */
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff,
                         @Param("batchSize") int batchSize);
}
