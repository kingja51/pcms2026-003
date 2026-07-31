package com.gonet.primary.notification.service;

import java.time.LocalDateTime;

/**
 * 알림 보존 정책 — 스케줄러와 관리자 수동 트리거가 공유하는 단일 진입점.
 *
 * <p>read 알림 90일 + deleted 알림 365일 정책. 두 단계 모두 멱등 — 같은 cutoff 로
 * 다시 호출해도 추가 영향 없음.
 */
public interface NotificationRetentionService {

    /** 현재 설정값(read-days/purge-days) 반환. */
    int readDays();
    int purgeDays();

    /** preview — 영향받을 행 수 미리 계산. 변경 없음. */
    Preview preview();

    /** 즉시 실행 — read soft delete + deleted purge. 결과 반환. */
    Result runNow();

    /** 미리보기 결과. */
    record Preview(LocalDateTime readCutoff,
                     LocalDateTime purgeCutoff,
                     long oldReadCount,
                     long oldDeletedCount) {
        public long totalAffected() { return oldReadCount + oldDeletedCount; }
    }

    /** 실행 결과. */
    record Result(LocalDateTime readCutoff,
                    LocalDateTime purgeCutoff,
                    int softDeleted,
                    int purged) {
        public int totalAffected() { return softDeleted + purged; }
    }
}
