package com.gonet.logging.log.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * log_audit 테이블 INSERT 용 DTO (logging DB).
 *
 * <p>감사로그 5경로 중 1경로 (DB 기록) — 나머지 경로:
 * <ol>
 *   <li>log_audit 테이블 (이 DTO)</li>
 *   <li>Logback JSON (file 기반, Loki 수집 대상)</li>
 *   <li>Spring ApplicationEvent (OTel/Kafka 익스포터 확장 지점)</li>
 *   <li>MariaDB server_audit plugin (DBA 구성)</li>
 *   <li>MariaDB binlog (DBA 구성, CDC 소스)</li>
 * </ol>
 *
 * <p>PK {@code log_audit_id} 는 AUTO_INCREMENT. 감사컬럼은 명시적으로 세팅
 * (logging DB 는 AuditInterceptor 미적용).
 */
@Getter
@Setter
public class AuditLog {

    private Long logAuditId;            // PK — viewer 화면용

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_LOGIN  = "LOGIN";
    public static final String ACTION_ACCESS = "ACCESS";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL    = "FAIL";
    public static final String RESULT_ERROR   = "ERROR";

    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;
    private String action;
    private String targetEntity;
    private String targetId;
    private String requestUri;
    private String httpMethod;
    private String clientIp;
    private String userAgent;
    private String beforeJson;
    private String afterJson;
    private String result;
    private String traceId;
    private LocalDateTime loggedAt;

    // 감사컬럼 명시 세팅 (logging DB)
    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
    private String        updatedBy;
    private String        updatedIp;
    private LocalDateTime updatedAt;
}
