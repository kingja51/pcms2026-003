package com.gonet.primary.system.pii.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_pii_purge_log INSERT 용 DTO (gopcms_primary).
 *
 * <p>개인정보보호법 §21 (개인정보 파기) + 「개인정보의 안전성 확보조치 기준 고시」 §16 의무.
 * 정보주체 PII 가 파기되는 모든 시점 (휴면 5년 만기, 탈퇴 보관 만료, 직원 퇴사, 본인 요청 등)
 * 에 본 테이블에 1행씩 적재.
 *
 * <p>{@code user_id_hash} 는 SHA-256(member_id|admin_id|employee_id) 의 hex —
 * 역추적 불가하지만 향후 동일 정보주체 식별 가능 (감사 추적 성격).
 *
 * <p>적재 진입점: {@link com.gonet.primary.system.pii.service.PiiPurgeLogger#write(PiiPurgeLog)}
 */
@Getter
@Setter
public class PiiPurgeLog {

    /** UUID v7 PK. */
    private String piiPurgeLogId;

    /** {@link PiiPurgeUserType#name()} — MEMBER / EMPLOYEE / ADMIN */
    private String userType;

    /** SHA-256(target user PK) hex 64 chars — 역추적 불가, 추적 가능. */
    private String userIdHash;

    /** 파기 시각. */
    private LocalDateTime purgedAt;

    /** {@link PiiPurgeReason#name()} 또는 자유 문자열 (예: "수동 파기 — 사유: ..."). */
    private String purgeReason;

    /** 파기 대상 테이블 CSV (예: "tb_member_dormant", "tb_member_dormant,tb_member_consent"). */
    private String tableList;

    /** 법적 근거 (PIPA §21 등). null 가능 — MANUAL_OPERATION 시 명시 권장. */
    private String legalBasis;

    // 6감사컬럼
    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
    private String        updatedBy;
    private String        updatedIp;
    private LocalDateTime updatedAt;
}
