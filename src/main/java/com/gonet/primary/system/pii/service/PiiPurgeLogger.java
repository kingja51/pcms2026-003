package com.gonet.primary.system.pii.service;

import com.gonet.primary.system.pii.dto.PiiPurgeLog;
import com.gonet.primary.system.pii.dto.PiiPurgeReason;
import com.gonet.primary.system.pii.dto.PiiPurgeUserType;

/**
 * 개인정보 파기 이력 적재 단일 진입점 — tb_pii_purge_log INSERT.
 *
 * <p>PIPA §21 (개인정보 파기) + 「개인정보의 안전성 확보조치 기준 고시」 §16 의무 항목.
 * 정보주체 PII 가 파기되는 모든 시점 (휴면 5년 만기, 탈퇴 보관 만료, 본인 요청 등)
 * 에 본 logger 호출 — 호출 누락 시 컴플라이언스 위반 가능.
 *
 * <p>호출 패턴:
 * <pre>
 * piiPurgeLogger.write(
 *   PiiPurgeUserType.MEMBER,
 *   memberId,                                      // 자동으로 SHA-256 hash
 *   PiiPurgeReason.MEMBER_DORMANT_EXPIRY,
 *   "tb_member_dormant"                            // table_list
 * );
 * </pre>
 *
 * <p>구현체는 §0.46 M3 + §0.48 회귀 수정 가르침대로 {@code @Lazy PiiPurgeLogger self} 자기 프록시
 * 주입 + {@link #writeAsync(PiiPurgeLog)} interface 노출로 self-invocation 함정 회피.
 */
public interface PiiPurgeLogger {

    /**
     * 파기 이력 적재. 호출자 트랜잭션과 분리된 별도 트랜잭션에서 INSERT 되므로
     * 호출자가 rollback 해도 본 이력은 보존.
     *
     * @param userType   정보주체 유형 (MEMBER/EMPLOYEE/ADMIN)
     * @param userPk     정보주체 PK (member_id/admin_id/employee_id) — 내부에서 SHA-256 변환
     * @param reason     표준 파기 사유 (legal_basis 자동 설정)
     * @param tableList  파기 대상 테이블 CSV (예: "tb_member_dormant", "tb_member_dormant,tb_member_consent")
     */
    void write(PiiPurgeUserType userType, String userPk, PiiPurgeReason reason, String tableList);

    /**
     * 파기 이력 적재 — 자유 사유. {@link PiiPurgeReason} 카탈로그에 없는 운영 케이스.
     *
     * @param freeReason  자유 문자열 (예: "GDPR right-to-erasure 요청")
     * @param legalBasis  법적 근거 (필수)
     */
    void writeWithFreeReason(PiiPurgeUserType userType, String userPk,
                              String freeReason, String legalBasis, String tableList);

    /**
     * <b>내부용 — self-invocation 회피.</b> {@code PiiPurgeLoggerImpl.write()} 가
     * {@code @Lazy PiiPurgeLogger self} 프록시를 통해 호출. 외부에서 직접 호출 금지.
     *
     * <p>{@code @Async} + {@code @Transactional(REQUIRES_NEW)} 가 적용되어
     * 호출자 트랜잭션과 분리된 별도 스레드 + 트랜잭션에서 실행.
     */
    void writeAsync(PiiPurgeLog row);
}
