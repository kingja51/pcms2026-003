package com.gonet.logging.privacy.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * log_privacy_access INSERT 용 DTO (logging DB).
 *
 * <p>개인정보보호법 §29 + 「개인정보의 안전성 확보조치 기준 고시」 §8 의무.
 *
 * <p>적재 진입점:
 * <ul>
 *   <li>{@code @PrivacyAccess} 어노테이션 + AOP — 명시적 메서드 경계 캡처</li>
 *   <li>{@link com.gonet.logging.privacy.service.PrivacyAccessLogger} 직접 호출 — 명시적 마킹</li>
 * </ul>
 *
 * <p>append-only 정책 — log_* 표준에 맞춰 created_* 만 보유 (updated_* 컬럼 부재).
 */
@Getter
@Setter
public class PrivacyAccessLog {

    private Long logPrivacyAccessId;

    public static final String ACTION_READ    = "READ";
    public static final String ACTION_SEARCH  = "SEARCH";
    public static final String ACTION_EXPORT  = "EXPORT";
    public static final String ACTION_PRINT   = "PRINT";
    public static final String ACTION_UPDATE  = "UPDATE";
    public static final String ACTION_DELETE  = "DELETE";
    public static final String ACTION_DECRYPT = "DECRYPT";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL    = "FAIL";
    public static final String RESULT_DENIED  = "DENIED";
    public static final String RESULT_ERROR   = "ERROR";

    // 접속자 (Who)
    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;

    // 접속지 (Where)
    private String clientIp;
    private String userAgent;
    private String sessionId;
    private String requestUri;
    private String httpMethod;
    private String traceId;

    // 정보주체 (Whose PII)
    private String  targetEntity;
    private String  targetUserType;
    private String  targetId;
    private Integer targetCount;

    // 처리 항목 / 업무 / 사유 / 결과
    private String piiFields;
    private String accessAction;
    private String accessReason;
    private String result;
    private String failReason;

    // 시각
    private LocalDateTime accessedAt;

    // append-only 감사컬럼
    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
}
