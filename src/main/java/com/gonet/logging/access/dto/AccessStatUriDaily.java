package com.gonet.logging.access.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * stat_access_uri_daily — URI 별 일별 집계.
 * 인기 페이지 랭킹용.
 */
@Getter
@Setter
public class AccessStatUriDaily {
    private Long          statId;
    private LocalDate     statDate;
    private String        siteId;
    private String        siteCode;
    private String        requestUri;
    private long          pv;
    private long          uv;
    private int           avgResponseMs;
    private LocalDateTime computedAt;
}
