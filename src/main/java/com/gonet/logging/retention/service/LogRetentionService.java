package com.gonet.logging.retention.service;

import java.time.LocalDateTime;

/**
 * logging DB 의 log_* 테이블 6개월 retention 정책 진입점.
 *
 * <p>각 테이블별로 cutoff 이전 행을 LIMIT batch 로 반복 DELETE.
 * 스케줄러({@link com.gonet.scheduler.LogRetentionScheduler})
 + 운영자 수동 트리거(추후 화면 추가) 둘 다에서 호출.
 */
public interface LogRetentionService {

    /** 4개 log_* 테이블 모두 정리. */
    PurgeResult purgeAll(LocalDateTime cutoff);

    long purgeLogAccess(LocalDateTime cutoff);
    long purgeLogAudit(LocalDateTime cutoff);
    long purgeLogLogin(LocalDateTime cutoff);
    long purgeLogFileDownload(LocalDateTime cutoff);
    long purgeLogError(LocalDateTime cutoff);

    /**
     * log_privacy_access 정리 — PIPA 안전성확보조치 고시 §8 보존 기간(24개월) 별도 적용.
     * 일반 log_* 의 cutoff 와 다른 시점을 받으므로 purgeAll 과 분리.
     */
    long purgeLogPrivacyAccess(LocalDateTime cutoff);

    /**
     * log_security 정리 — 보안 이벤트 분리 적재(§0.48). 일반 log_* 와 동일 6개월 기본,
     * 운영 정책상 별도 보존이 필요하면 {@code gopcms.log.security.retention-months} 로 override.
     */
    long purgeLogSecurity(LocalDateTime cutoff);

    /**
     * <b>dry-run 미리보기</b> — 아무것도 지우지 않고 대상 건수만 센다.
     *
     * <p>로그 삭제는 되돌릴 수 없고, 감사·접속 로그는 사고가 난 뒤에야 필요해진다.
     * 보존 개월수를 잘못 넣어 "12개월"이 "12일"로 동작하는 식의 실수는
     * 지우고 나서는 확인할 방법이 없다. 켜기 전에 건수를 볼 수 있어야 한다.
     *
     * <p>삭제와 <b>같은 술어</b>를 쓰되 batchSize 는 적용하지 않는다 —
     * 삭제는 배치로 나눠 돌지만 미리보기는 전체 건수를 알려줘야 하기 때문이다.
     *
     * @param cutoff        일반 log_* 기준 시점
     * @param privacyCutoff log_privacy_access 기준 시점(보존 기간이 다르다)
     */
    PurgeResult preview(LocalDateTime cutoff, LocalDateTime privacyCutoff);

    /** 정리 결과 — 운영 모니터링용. */
    record PurgeResult(long access, long audit, long login, long fileDownload, long error) {
        public long total() { return access + audit + login + fileDownload + error; }
    }
}
