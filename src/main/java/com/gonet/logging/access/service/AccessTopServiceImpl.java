package com.gonet.logging.access.service;

import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.access.dto.AccessTopRow;
import com.gonet.logging.access.mapper.AccessTopMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AccessTopService} 구현 — 11종 TOP-N 쿼리를 순차 실행하여 한 묶음으로 반환.
 *
 * <p>모든 SELECT 는 logging DB 의 log_access / log_file_download 직접 조회.
 * 트랜잭션 부여하지 않음 (read-only multi-statement, MyBatis 기본 autoCommit).
 */
@Service
@Transactional(readOnly = true, transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
public class AccessTopServiceImpl extends EgovAbstractServiceImpl implements AccessTopService {

    private final AccessTopMapper mapper;

    public AccessTopServiceImpl(AccessTopMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, List<AccessTopRow>> all(LocalDate from, LocalDate to,
                                                  int limit, int slowMinCalls) {
        Map<String, List<AccessTopRow>> r = new LinkedHashMap<>();
        r.put("topClientIps",       mapper.topClientIps(from, to, limit));
        r.put("topRefererDomains",  mapper.topRefererDomains(from, to, limit));
        r.put("topErrorIps",        mapper.topErrorIps(from, to, limit));
        r.put("topSlowUris",        mapper.topSlowUris(from, to, slowMinCalls, limit));
        r.put("topAnonymousUris",   mapper.topAnonymousUris(from, to, limit));
        r.put("topMenuPaths",       mapper.topMenuPaths(from, to, limit));
        r.put("topUserAgents",      mapper.topUserAgents(from, to, limit));
        r.put("topUriScannerIps",   mapper.topUriScannerIps(from, to, limit));
        r.put("topBandwidthIps",    mapper.topBandwidthIps(from, to, limit));
        r.put("top404Uris",         mapper.top404Uris(from, to, limit));
        r.put("topDownloadFiles",   mapper.topDownloadFiles(from, to, limit));
        r.put("topLoginUsers",      mapper.topLoginUsers(from, to, limit));
        return r;
    }
}
