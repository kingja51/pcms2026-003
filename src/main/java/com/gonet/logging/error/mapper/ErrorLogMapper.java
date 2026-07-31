package com.gonet.logging.error.mapper;

import com.gonet.logging.error.dto.ErrorLog;
import com.gonet.logging.viewer.dto.LogSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * log_error CRUD — INSERT (자동 캡처) + 관리자 viewer 조회.
 */
@EgovMapper
public interface ErrorLogMapper {

    int insert(ErrorLog log);

    List<ErrorLog> findRecent(@Param("search") LogSearch search);

    /** 페이징용 총 건수 — findRecent 와 동일한 WHERE */
    long countRecent(@Param("search") LogSearch search);

    ErrorLog findById(@Param("id") long id);

    /** trace_id 로 같은 요청의 모든 error 조회 */
    List<ErrorLog> findByTraceId(@Param("traceId") String traceId);
}
