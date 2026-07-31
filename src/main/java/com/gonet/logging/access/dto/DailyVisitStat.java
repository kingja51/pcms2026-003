package com.gonet.logging.access.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * stat_daily_visit row — 설계서 §10.4 표준 일별 방문 통계.
 * PK = (stat_date, site_id).
 */
@Getter
@Setter
public class DailyVisitStat {
    private LocalDate statDate;
    private String    siteId;
    private String    siteCode;     // 화면 표시용 JOIN 결과
    private long      visitCount;
    private long      uniqueCount;
    private long      pvCount;
}
