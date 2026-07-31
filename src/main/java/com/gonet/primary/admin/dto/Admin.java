package com.gonet.primary.admin.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.crypto.Encrypt;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * tb_admin 1건 — 관리자 CRUD 엔티티.
 *
 * <p>PII 필드({@link Encrypt}): {@code adminName}, {@code email}, {@code phone},
 * {@code twoFactorSecret} — MyBatis {@code EncryptInterceptor} 가 I/O 경계에서 {AG}... 자동 처리.
 * <p>검색/중복확인용 {@code emailHash} 는 HMAC-SHA256 결과(hex) — 서비스 계층에서 직접 세팅.
 * <p>목록 JOIN 표시용: {@code adminGroupName}.
 */
@Getter
@Setter
public class Admin extends BaseEntity implements SoftDeletable {

    private String        adminId;
    private Long          adminSeq;
    private String        adminGroupId;
    private String        loginId;
    private String        password;             // BCrypt
    private LocalDateTime passwordChangedAt;
    private LocalDateTime passwordExpireAt;
    private String        roleIds;              // CSV (계층 확장, UUID)
    private String        roleCodes;            // CSV (role_code 텍스트, 예: ROLE_ADMIN,ROLE_STAFF)
    private String        groupIds;             // CSV

    @Encrypt private String adminName;
    @Encrypt private String email;
    private String          emailHash;          // HMAC-SHA256 (hex)
    @Encrypt private String phone;

    /** 부서 ID — FK tb_department. Gemini 문서검색 가시성 필터 기준. nullable. */
    private String        departmentId;
    /** 부서명 — denormalized cache. tb_department.department_name 변경 시 동기화 대상. */
    private String        departmentName;

    private String        twoFactorEnabledYn;
    @Encrypt private String twoFactorSecret;

    private String        ipWhitelist;
    private LocalTime     allowedTimeFrom;
    private LocalTime     allowedTimeTo;

    private String        status;               // ACTIVE/LOCKED/INACTIVE/SUSPENDED
    private Integer       loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private String        lastLoginIp;
    private LocalDateTime lastAccessAt;

    private String        remarks;
    private String        deleteYn;

    // JOIN 표시용
    private String        adminGroupName;
}
