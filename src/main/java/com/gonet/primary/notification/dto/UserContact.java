package com.gonet.primary.notification.dto;

import com.gonet.common.crypto.Encrypt;
import lombok.Getter;
import lombok.Setter;

/**
 * user_id → 표시명/email 단순 lookup row.
 *
 * <p>{@code @Encrypt} 가 붙은 필드를 EncryptInterceptor 가 ResultSet 단계에서 자동 복호화 한다 —
 * Mapper 가 본 클래스를 result type 으로 사용해야 평문 email 을 얻을 수 있다.
 *
 * <p>{@code userType} 은 어느 테이블에서 매치됐는지 표시 — MEMBER/EMPLOYEE/ADMIN.
 */
@Getter
@Setter
public class UserContact {

    private String userId;
    private String userType;
    private String loginId;

    @Encrypt private String email;
    @Encrypt private String name;
}
