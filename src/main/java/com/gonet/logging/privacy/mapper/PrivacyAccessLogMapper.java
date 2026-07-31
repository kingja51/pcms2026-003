package com.gonet.logging.privacy.mapper;

import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import com.gonet.logging.viewer.dto.LogSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * log_privacy_access INSERT + viewer 조회 Mapper (logging DB).
 */
@EgovMapper
public interface PrivacyAccessLogMapper {

    void insert(PrivacyAccessLog row);

    List<PrivacyAccessLog> findRecent(@Param("search") LogSearch search);

    long countRecent(@Param("search") LogSearch search);

    PrivacyAccessLog findById(@Param("id") long id);

    /** trace_id timeline 통합 viewer 용 */
    List<PrivacyAccessLog> findByTraceId(@Param("traceId") String traceId);
}
