package com.gonet.logging.access.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * log_access 테이블 INSERT DTO (logging DB).
 *
 * <p>모든 HTTP 요청을 PostFilter 가 비동기로 기록. 정적 리소스/health 등은 제외.
 * 통계 화면은 본 테이블이 아닌 stat_access_daily / stat_access_uri_daily 만 조회.
 */
@Getter
@Setter
public class AccessLog {

    private Long   logAccessId;          // PK — viewer 화면용
    private String siteId;
    private String siteCode;
    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;
    private String requestUri;
    private String httpMethod;
    private String queryString;
    private String referer;
    private String clientIp;
    private String userAgent;
    private Integer statusCode;
    private Integer responseMs;
    private Long bytesSent;
    private String sessionId;
    private String traceId;
    private LocalDateTime loggedAt;

    // 감사컬럼 명시
    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
    private String        updatedBy;
    private String        updatedIp;
    private LocalDateTime updatedAt;
}
