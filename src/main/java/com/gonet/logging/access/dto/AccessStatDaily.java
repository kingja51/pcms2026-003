package com.gonet.logging.access.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * stat_access_daily — 일별 접근 통계 집계 row.
 *
 * <p>차원: stat_date / site_id / user_type / bucket_hour
 * <p>측정값: pv / uv / unique_users / response_ms / err_4xx / err_5xx / bytes_sent
 */
@Getter
@Setter
public class AccessStatDaily {

    private Long          statId;
    private LocalDate     statDate;
    private String        siteId;
    private String        siteCode;
    private String        userType;     // ANONYMOUS|MEMBER|EMPLOYEE|ADMIN, NULL=전체
    private Integer       bucketHour;   // 0~23, NULL=하루 전체

    private long          pv;
    private long          uv;
    private long          uniqueUsers;
    private int           avgResponseMs;
    private int           p95ResponseMs;
    private long          err4xx;
    private long          err5xx;
    private long          bytesSent;
    private LocalDateTime computedAt;
}
