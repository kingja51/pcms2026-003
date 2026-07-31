package com.gonet.primary.member.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_member_withdraw — 탈퇴 회원 감사 행 (법정 최소 항목만).
 *
 * <p>평문 PII 없음 — login_id_hash (HMAC-SHA256) + di_hash 만 보관.
 * §0.34 (2026-05-02) 정책상 CI 는 시스템 어디에도 적재 안 함 — DI 만 사용.
 * retention_expire_at 만료 시 WithdrawPurgeScheduler 가 영구 삭제 (§0.38).
 */
@Getter
@Setter
public class MemberWithdraw extends BaseEntity {

    private String        memberId;
    private Long          memberSeq;
    private String        siteId;
    private String        loginIdHash;
    private String        diHash;
    private LocalDateTime withdrawAt;
    /** 카테고리 코드 — USER_REQUEST / ADMIN_FORCE / DORMANT_EXPIRED. */
    private String        withdrawStatus;
    /** 자유 텍스트 — 실제 탈퇴 사유 (관리자 강제 탈퇴 시 5자 이상 필수). */
    private String        withdrawReason;
    private LocalDateTime retentionExpireAt;
    private String        legalBasis;

    // 화면 표시용 JOIN 필드 — site_code
    private String        siteCode;
}
