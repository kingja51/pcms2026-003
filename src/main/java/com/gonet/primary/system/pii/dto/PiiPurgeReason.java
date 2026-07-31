package com.gonet.primary.system.pii.dto;

/**
 * tb_pii_purge_log.purge_reason 카탈로그 — 운영 일관성을 위해 enum 으로 표준화.
 *
 * <p>name() 이 그대로 DB 에 저장. 한글 설명은 {@link #description()} (감사 가독성용).
 *
 * <p>법적 근거 ({@link #legalBasis()}) 는 PIPA §21조 (개인정보의 파기) 및
 * 안전성 확보조치 기준 고시 §16 (개인정보의 파기) 인용.
 */
public enum PiiPurgeReason {

    /** 회원 휴면 5년 보관 후 만기 파기 — PIPA §21조 + 「개인정보의 안전성 확보조치 기준 고시」 §16. */
    MEMBER_DORMANT_EXPIRY(
        "휴면 회원 5년 보관 만료",
        "개인정보보호법 §21조 / 안전성확보조치 §16"),

    /** 회원 탈퇴 보관 만기 파기 (예: 90일 등 보관 정책 종료). */
    MEMBER_WITHDRAW_EXPIRY(
        "탈퇴 회원 보관 만료",
        "개인정보보호법 §21조 / 안전성확보조치 §16"),

    /** 직원 퇴사 후 보관 기간 만료 파기. */
    EMPLOYEE_RETIREMENT_EXPIRY(
        "퇴사 직원 보관 만료",
        "개인정보보호법 §21조 / 근로기준법 §42"),

    /** 관리자 탈퇴 후 보관 기간 만료 파기. */
    ADMIN_RETIREMENT_EXPIRY(
        "탈퇴 관리자 보관 만료",
        "개인정보보호법 §21조 / 안전성확보조치 §16"),

    /** 정보주체 본인 즉시 파기 요청 (PIPA §36 정정·삭제 요구권). */
    SUBJECT_REQUEST(
        "정보주체 파기 요청",
        "개인정보보호법 §36조 (정정·삭제 요구권)"),

    /** 운영 장애 / 데이터 무결성 이슈로 인한 수동 파기. legal_basis 별도 기록 필수. */
    MANUAL_OPERATION(
        "운영자 수동 파기",
        null);

    private final String description;
    private final String legalBasis;

    PiiPurgeReason(String description, String legalBasis) {
        this.description = description;
        this.legalBasis  = legalBasis;
    }

    public String description() { return description; }
    public String legalBasis()  { return legalBasis; }
}
