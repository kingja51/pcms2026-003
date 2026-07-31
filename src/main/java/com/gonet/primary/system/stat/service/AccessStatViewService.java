package com.gonet.primary.system.stat.service;

import com.gonet.logging.access.dto.AccessStatDaily;
import com.gonet.logging.access.dto.AccessStatUriDaily;
import com.gonet.logging.access.dto.DailyVisitStat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 접근 로그 통계 화면 Service — 집계 테이블만 조회.
 *
 * <p>5개 통계:
 * <ol>
 *   <li>일별 PV/UV 시계열</li>
 *   <li>사이트별 트래픽 점유율</li>
 *   <li>사용자 유형별 분포</li>
 *   <li>시간대별 트래픽 패턴 (0~23)</li>
 *   <li>인기 페이지 TOP N</li>
 * </ol>
 *
 * <p>기간 단위: 일/주/월/년 — 화면 토글로 from/to 범위만 바꿈.
 */
public interface AccessStatViewService {

    Map<String, Object> summary(LocalDate from, LocalDate to, String siteId);

    List<AccessStatDaily> dailySeries(LocalDate from, LocalDate to, String siteId);

    List<AccessStatDaily> siteShare(LocalDate from, LocalDate to);

    List<AccessStatDaily> userTypeShare(LocalDate from, LocalDate to, String siteId);

    List<AccessStatDaily> hourlyPattern(LocalDate from, LocalDate to, String siteId);

    List<AccessStatUriDaily> topPages(LocalDate from, LocalDate to, String siteId, int limit);

    // ── stat_daily_visit (표준 통계) ──
    List<DailyVisitStat> dailyVisit(LocalDate from, LocalDate to, String siteId);

    List<DailyVisitStat> dailyVisitBySite(LocalDate from, LocalDate to);
}
