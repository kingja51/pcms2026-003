package com.gonet.primary.member.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_member_password_history — 회원 비밀번호 이력 (재사용 방지).
 *
 * <p>{@code passwordHash} 는 BCrypt — 평문 노출 없음. 관리자는 변경 시각/IP 만 본다.
 */
@Getter
@Setter
public class MemberPasswordHistory extends BaseEntity {

    private String        pwdHistoryId;
    private String        memberId;
    /** BCrypt — 운영자 화면에서 평문 노출 절대 금지. 상세에서도 마스킹 처리. */
    private String        passwordHash;
    private LocalDateTime changedAt;
}
