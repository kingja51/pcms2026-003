package com.gonet.common.retention;

import java.time.LocalDateTime;

/**
 * 도메인 별 soft-delete 정리 책임.
 *
 * <p>각 도메인이 본 인터페이스의 구현체를 {@code @Component} 로 등록하면
 * {@link com.gonet.scheduler.SoftDeleteRetentionScheduler} 가 자동 수집·실행한다.
 *
 * <h2>FK 종속성</h2>
 * 자식 → 부모 순서로 정리되어야 외래키 제약 위반을 피할 수 있다. 같은 cutoff 안에서
 * 자식이 먼저 hard delete 되어야 부모 row 의 hard delete 가 통과한다.
 * {@link #childOrder()} 가 작은 값일수록 먼저 실행 — 자식 도메인이 작은 값을 반환.
 *
 * <h2>구현 가이드</h2>
 * <ul>
 *   <li>구현체는 {@code @Component} 로 등록 (스케줄러가 List 주입으로 자동 수집)</li>
 *   <li>도메인 mapper 에 {@code purgeSoftDeletedOlderThan(cutoff)} 1 메서드만 추가</li>
 *   <li>본 메서드의 트랜잭션은 호출 측({@link com.gonet.scheduler.SoftDeleteRetentionScheduler})
 *       이 도메인별로 새 트랜잭션을 열어 호출하므로 구현체는 추가 트랜잭션 어노테이션 불필요</li>
 * </ul>
 */
public interface RetentionTarget {

    /** 정리 대상 테이블명 — 로그·감사·application.yml buckets 키와 일치해야 한다. */
    String tableName();

    /** 보존 기간 bucket. application.yml 의 buckets 매핑이 본 값을 override 가능. */
    RetentionBucket defaultBucket();

    /**
     * 작은 값일수록 먼저 실행. 자식 도메인은 작은 값, 부모 도메인은 큰 값.
     * 같은 값이면 빈 등록 순서 (Spring 측 비결정적) — 다른 도메인과 충돌 가능성이 있으면
     * 명시적으로 다른 값을 부여.
     */
    int childOrder();

    /**
     * cutoff 이전(updated_at &lt;= cutoff) 의 soft-deleted 행을 hard delete.
     *
     * @return 삭제된 행 수
     */
    int purge(LocalDateTime cutoff);

    /**
     * cutoff 이전 soft-deleted 행 수만 카운트 — DELETE 없이.
     * dry-run 모드에서 호출되어 운영자가 안전 검증 후 활성화 결정에 활용.
     */
    int countCandidates(LocalDateTime cutoff);
}
