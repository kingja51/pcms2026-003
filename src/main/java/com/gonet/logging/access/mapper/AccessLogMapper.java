package com.gonet.logging.access.mapper;

import com.gonet.logging.access.dto.AccessLog;
import com.gonet.logging.viewer.dto.LogSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * log_access INSERT + 배치 집계 source 쿼리 Mapper.
 * 통계 조회는 {@link AccessStatMapper} 사용. 관리자 viewer 는 {@link #findRecent} / {@link #findById} 사용.
 */
@EgovMapper
public interface AccessLogMapper {

    void insert(AccessLog log);

    /** 어제 분 record 수 (집계 사전 검증용) */
    long countByDate(@Param("statDate") java.time.LocalDate statDate);

    /** 관리자 viewer — 페이징 + keyword/날짜 검색 */
    List<AccessLog> findRecent(@Param("search") LogSearch search);

    /** 페이징용 총 건수 — findRecent 와 동일한 WHERE */
    long countRecent(@Param("search") LogSearch search);

    AccessLog findById(@Param("id") long id);

    /** trace_id 로 같은 요청의 모든 access 행 조회 — Timeline viewer 용 */
    List<AccessLog> findByTraceId(@Param("traceId") String traceId);
}
