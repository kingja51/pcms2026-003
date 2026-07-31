package com.gonet.common.lifecycle;

import java.time.LocalDateTime;

/**
 * 도메인별 탈퇴(withdraw) 만료 영구 삭제 책임.
 *
 * <p>{@code tb_*_withdraw} 테이블의 {@code retention_expire_at} 컬럼이 만료된 행을
 * hard delete. 도메인별 보존 기간은 등록 시점에 {@code retention_expire_at} 컬럼에 부여 —
 * 회원 1년 / 관리자 3년 / 직원 5년 등 운영 정책 차별 가능.
 *
 * <p>Soft-delete retention ({@link com.gonet.common.retention.RetentionTarget}) 과의
 * 차이점:
 * <ul>
 *   <li>retention: {@code delete_yn='Y'} + {@code updated_at} 기준 (일반 도메인 정리)</li>
 *   <li>withdraw-purge: {@code retention_expire_at} 기준 (PII / 법령 보관 의무)</li>
 * </ul>
 *
 * <p>도메인별 구현체는 {@code @Component} 로 등록만 하면 {@link
 * com.gonet.scheduler.WithdrawPurgeScheduler} 가 자동 수집·실행.
 */
public interface WithdrawPurgeTarget {

    /** 정리 대상 테이블명 — 로그/감사/설정 키와 일치. */
    String tableName();

    /**
     * {@code retention_expire_at <= now} 인 row 를 hard delete.
     *
     * @return 삭제된 row 수
     */
    int purgeExpired(LocalDateTime now);

    /** dry-run — 만료 후보 행 수만 카운트, DELETE 없음. */
    int countExpired(LocalDateTime now);
}
