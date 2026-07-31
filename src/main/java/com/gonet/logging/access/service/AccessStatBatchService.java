package com.gonet.logging.access.service;

import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.access.mapper.AccessLogMapper;
import com.gonet.logging.access.mapper.AccessStatMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * log_access 를 stat_access_daily / stat_access_uri_daily / stat_daily_visit 로 집계하는
 * 배치 비즈니스 로직.
 *
 * <p>스케줄(@Scheduled) 트리거는 {@code com.gonet.scheduler.AccessStatScheduler} 가 담당 —
 * 본 클래스는 {@link #aggregateFor(LocalDate)} 진입점만 노출.
 *
 * <p>멱등성: 같은 statDate 로 재실행하면 기존 행 DELETE 후 재집계 — 운영 중 보강·재계산 가능.
 */
@Service
public class AccessStatBatchService extends EgovAbstractServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(AccessStatBatchService.class);

    private final AccessLogMapper  logMapper;
    private final AccessStatMapper statMapper;

    public AccessStatBatchService(AccessLogMapper logMapper, AccessStatMapper statMapper) {
        this.logMapper  = logMapper;
        this.statMapper = statMapper;
    }

    /**
     * 임의 일자 집계 진입점. 스케줄러/관리자 수동 트리거 모두 본 메서드 호출.
     * 멱등(DELETE + INSERT, ON DUPLICATE KEY UPDATE) 으로 같은 statDate 반복 호출 안전.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
        transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
    public int aggregateFor(LocalDate statDate) {
        long src = logMapper.countByDate(statDate);
        log.info("===ACCESS_STAT_AGG_BEGIN date={} sourceRows={}", statDate, src);

        if (src == 0) {
            // 빈 날도 상위 코드가 "이미 집계됨" 으로 인식하도록 빈 행을 만들지 않고 리턴.
            log.info("===ACCESS_STAT_AGG_SKIP date={} (no source rows)", statDate);
            return 0;
        }

        statMapper.deleteDaily(statDate);
        int dailyRows = statMapper.aggregateDaily(statDate);

        statMapper.deleteUriDaily(statDate);
        int uriRows = statMapper.aggregateUriDaily(statDate);

        // 설계서 §10.4 표준 통계 — ON DUPLICATE KEY UPDATE 라 멱등
        int visitRows = statMapper.aggregateDailyVisit(statDate);

        log.info("===ACCESS_STAT_AGG_OK date={} dailyRows={} uriRows={} visitRows={}",
            statDate, dailyRows, uriRows, visitRows);
        return dailyRows;
    }
}
