package com.gonet.logging.error.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * log_error 테이블 INSERT/조회 DTO (logging DB).
 *
 * <p>{@link com.gonet.config.access.ErrorLoggingExceptionResolver} 가
 * Spring MVC 미처리 예외 발생 시 자동 채워서 INSERT.
 *
 * <p>로그 컬럼 정책: log_* 는 INSERT only — updated_xxx 컬럼 없음.
 */
@Getter
@Setter
public class ErrorLog {

    private Long          logErrorId;          // PK — viewer 화면용

    // 사이트/사용자 컨텍스트
    private String        siteId;
    private String        siteCode;
    private String        actorUserId;
    private String        actorUserType;
    private String        actorLoginId;

    // 요청 정보
    private String        requestUri;
    private String        httpMethod;
    private String        queryString;
    private String        clientIp;
    private String        userAgent;

    // 예외 정보
    private String        errorClass;       // FQCN
    private String        errorMessage;     // throwable.getMessage(), 2000자 cap
    private String        stackTrace;       // 32KB cap
    private Integer       statusCode;

    // 분산 추적
    private String        traceId;
    private String        sessionId;

    // 시각
    private LocalDateTime loggedAt;

    // 감사 (INSERT 시점)
    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
}
