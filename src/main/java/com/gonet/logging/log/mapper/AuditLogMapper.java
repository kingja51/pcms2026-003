package com.gonet.logging.log.mapper;

import com.gonet.logging.log.dto.AuditLog;
import com.gonet.logging.viewer.dto.LogSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * log_audit INSERT + 관리자 viewer 조회 Mapper (logging DB).
 */
@EgovMapper
public interface AuditLogMapper {

    void insert(AuditLog log);

    /** 관리자 viewer — 페이징 + keyword/날짜 검색 */
    List<AuditLog> findRecent(@Param("search") LogSearch search);

    /** 페이징용 총 건수 — findRecent 와 동일한 WHERE */
    long countRecent(@Param("search") LogSearch search);

    AuditLog findById(@Param("id") long id);

    /** trace_id 로 같은 요청의 모든 audit 이벤트 조회 */
    List<AuditLog> findByTraceId(@Param("traceId") String traceId);
}
