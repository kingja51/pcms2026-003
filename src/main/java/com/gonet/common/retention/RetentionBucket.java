package com.gonet.common.retention;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Soft-delete 보존 기간 bucket.
 *
 * <p>각 도메인은 자신의 데이터 성격에 맞는 bucket 을 선언하고, 스케줄러가 실행 시점에
 * {@link #cutoff(LocalDateTime)} 로 cutoff 시각을 계산하여 mapper 에 전달한다.
 *
 * <p>새 보존 기간이 필요하면 enum 값만 추가하면 됨 — application.yml 의 buckets 매핑이
 * 그 enum 이름(예: {@code DAYS_365}) 을 그대로 참조한다.
 */
public enum RetentionBucket {

    /** 1일 — 회원. */
    DAYS_1(1),
	
    /** 30일 — 일반 게시글/댓글/첨부 등. 사용자 실수 복구 여유 1개월. */
    DAYS_30(30),

    /** 90일 — 콘텐츠/마스터 데이터 등 운영 의사결정에 시간 여유가 필요한 도메인. */
    DAYS_90(90),

    /** 180일 — 회원/직원/감사 의무가 있는 PII 도메인. GDPR right-to-erasure 정합. */
    DAYS_180(180);

    private final int days;

    RetentionBucket(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }

    /** 기준 시각에서 보존 일수를 뺀 cutoff. updated_at &lt;= cutoff 인 행이 정리 대상. */
    public LocalDateTime cutoff(LocalDateTime now) {
        return now.minus(days, ChronoUnit.DAYS);
    }
}
