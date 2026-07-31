package com.gonet.primary.system.login.dto;

import com.gonet.common.crypto.Encrypt;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * {@code v_user_login} VIEW 1건 매핑 DTO.
 *
 * <p>관리자/직원/회원 3분할 테이블을 UNION 한 통합 로그인 VIEW.
 * 컬럼 정의는 schema_mariadb.sql §1.15 참고.
 */
@Getter
@Setter
public class UserLogin {

    private String        userType;            // 'MEMBER'/'STAFF' (003 은 EMPLOYEE 제외)
    private String        userId;              // UUID {entity}_id
    private Long          uniqId;              // {entity}_seq BIGINT
    private String        siteId;              // 관리자/회원만, 직원은 NULL
    private String        groupId;             // 관리자 그룹 ID (관리자만)
    /** 부서 ID — 직원/관리자만 값 보유. 회원은 빈 문자열('') 또는 NULL. Gemini 문서검색 가시성 필터 기준. */
    private String        departmentId;
    /** 부서명 — denormalized cache. 회원은 빈 문자열 또는 NULL. */
    private String        departmentName;

    private String        loginId;
    private String        password;            // BCrypt
    private String        status;              // ACTIVE/LOCKED/DORMANT/WITHDRAWN/EMAIL_PENDING
    private Integer       loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime passwordExpireAt;

    private String        twoFactorEnabledYn;  // 'Y'/'N'
    @Encrypt private String twoFactorSecret;   // 관리자/직원 — DB {AG} 암호문, 읽기 시 자동 복호화

    private String        ipWhitelist;
    private LocalTime     allowedTimeFrom;
    private LocalTime     allowedTimeTo;

    private String        roleIds;             // CSV — 계층 확장 저장 (UUID)
    private String        roleCodes;           // CSV — role_code 텍스트 (예: ROLE_ADMIN,ROLE_STAFF)
    private String        groupIds;            // CSV
    private String        deleteYn;
}
