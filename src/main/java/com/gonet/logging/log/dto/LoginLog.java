package com.gonet.logging.log.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * log_login 테이블 INSERT 용 DTO.
 * PK log_login_id 는 AUTO_INCREMENT — 설정하지 않음.
 * 감사컬럼 6종은 BaseEntity + AuditInterceptor 가 처리(로깅 DB 는 현재 미적용이므로
 * 명시적으로 세팅 권장).
 */
@Getter
@Setter
public class LoginLog extends BaseEntity {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL    = "FAIL";
    public static final String RESULT_LOCKED  = "LOCKED";
    public static final String RESULT_2FA_FAIL = "2FA_FAIL";

    private Long   logLoginId;          // PK — viewer 화면용

    private String userId;
    private String userType;
    private String loginId;
    private String clientIp;
    private String userAgent;
    private String result;
    private String failReason;
    private String sessionId;
    private LocalDateTime loggedAt;
}
