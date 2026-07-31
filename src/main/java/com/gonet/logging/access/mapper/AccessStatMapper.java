package com.gonet.logging.access.mapper;

import com.gonet.logging.access.dto.AccessStatDaily;
import com.gonet.logging.access.dto.AccessStatUriDaily;
import com.gonet.logging.access.dto.DailyVisitStat;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * stat_access_daily / stat_access_uri_daily — 통계 화면 조회 + 배치 INSERT.
 *
 * <p>모든 조회는 집계 테이블에서만 — log_access 직접 SELECT 금지(서버 부하).
 */
@EgovMapper
public interface AccessStatMapper {

    // ──────────────────────────────────────────────────────────────
    // 배치 INSERT — INSERT ... SELECT 한 방으로 어제 분 집계
    // ──────────────────────────────────────────────────────────────

    /** stat_access_daily 의 statDate 분 데이터 삭제 (재계산 용) */
    int deleteDaily(@Param("statDate") LocalDate statDate);

    /** stat_access_daily INSERT — log_access 의 statDate 분을 집계 */
    int aggregateDaily(@Param("statDate") LocalDate statDate);

    /** stat_access_uri_daily 의 statDate 분 데이터 삭제 */
    int deleteUriDaily(@Param("statDate") LocalDate statDate);

    /** stat_access_uri_daily INSERT — URI 별 집계 (TOP 100 만 보관) */
    int aggregateUriDaily(@Param("statDate") LocalDate statDate);

    // ──────────────────────────────────────────────────────────────
    // 통계 화면 조회
    // ──────────────────────────────────────────────────────────────

    /** 일별 PV/UV — 차트용 시계열 (날짜 범위 + 사이트 필터) */
    List<AccessStatDaily> dailySeries(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("siteId") String siteId);

    /** 사이트별 트래픽 누적 (기간 합산) */
    List<AccessStatDaily> siteShareSummary(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /** 사용자 유형별 분포 (기간 합산) */
    List<AccessStatDaily> userTypeShareSummary(@Param("from") LocalDate from,
                                               @Param("to") LocalDate to,
                                               @Param("siteId") String siteId);

    /** 시간대별 트래픽 패턴 (기간 합산, hour 0~23) */
    List<AccessStatDaily> hourlySummary(@Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("siteId") String siteId);

    /** 인기 페이지 TOP N */
    List<AccessStatUriDaily> topPages(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("siteId") String siteId,
                                      @Param("limit") int limit);

    /** 기간 요약 카드 — pv/uv/err_4xx/err_5xx/avg_response_ms */
    Map<String, Object> periodSummary(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("siteId") String siteId);

    // ──────────────────────────────────────────────────────────────
    // stat_daily_visit (설계서 §10.4 표준)
    // ──────────────────────────────────────────────────────────────

    /** 어제 분 stat_daily_visit ON DUPLICATE KEY UPDATE INSERT */
    int aggregateDailyVisit(@Param("statDate") LocalDate statDate);

    /** 일별 방문 통계 — 차트용 (사이트별, 기간 범위) */
    List<DailyVisitStat> listDailyVisit(@Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("siteId") String siteId);

    /** 사이트 합계 — 기간 합산 (사이트 KPI 카드) */
    List<DailyVisitStat> sumDailyVisitBySite(@Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
