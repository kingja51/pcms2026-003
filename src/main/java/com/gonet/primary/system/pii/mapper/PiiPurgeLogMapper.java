package com.gonet.primary.system.pii.mapper;

import com.gonet.primary.system.pii.dto.PiiPurgeLog;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_pii_purge_log INSERT + 조회 Mapper (primary DB).
 *
 * <p>I-024 규약 — maria/mysql/postgres XML 3종 동거.
 */
@EgovMapper
public interface PiiPurgeLogMapper {

    void insert(PiiPurgeLog row);

    /** 운영 모니터링 — 최근 N일 파기 건수. */
    long countSince(@Param("since") LocalDateTime since);

    /** 운영 모니터링 — 최근 파기 이력 (관리자 화면 표시용). */
    List<PiiPurgeLog> findRecent(@Param("limit") int limit);
}
