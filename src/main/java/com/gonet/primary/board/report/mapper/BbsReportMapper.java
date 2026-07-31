package com.gonet.primary.board.report.mapper;

import com.gonet.primary.board.report.dto.BbsReport;
import com.gonet.primary.board.report.dto.BbsReportSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.util.List;

/**
 * tb_bbs_report Mapper — 사용자 신고 적재 + 관리자 모더레이션 조회/처리.
 */
@EgovMapper
public interface BbsReportMapper {

    /** 같은 신고자가 같은 대상에 이미 신고했는지 확인 (UNIQUE 회피). */
    BbsReport findByTargetAndReporter(@Param("targetType") String targetType,
                                        @Param("targetId") String targetId,
                                        @Param("reporterUserId") String reporterUserId);

    int insert(BbsReport report);

    /** 단건 신고 — 관리자 detail 화면. */
    BbsReport findById(@Param("reportId") String reportId);

    /** 관리자 모더레이션 목록 — JOIN 으로 target/reporter 메타 동시 노출. */
    List<BbsReport> findList(@Param("search") BbsReportSearch search);

    int countList(@Param("search") BbsReportSearch search);

    /** 동일 대상의 활성 신고 수 — 자동 REPORTED 전환 임계 비교용. */
    long countActiveByTarget(@Param("targetType") String targetType,
                              @Param("targetId") String targetId);

    /** 관리자 검토 처리 — status / reviewer / note 갱신. 6감사컬럼은 자동 주입. */
    int updateReview(@Param("reportId") String reportId,
                      @Param("status") String status,
                      @Param("reviewedBy") String reviewedBy,
                      @Param("reviewNote") String reviewNote);

    /**
     * 대상 (target_type, target_id) 의 모든 PENDING 신고를 일괄 REVIEWED 로 전이.
     * 자동 REPORTED 임계 도달 시점 또는 관리자가 REPORTED → PUBLISHED 복귀시킬 때 호출.
     *
     * <p>의미: "이미 모더레이션 절차로 들어갔거나 검토가 끝났으므로 PENDING 으로 누적된
     * 신고들은 모두 처리 완료 상태로 정리한다." 이후 {@link #countActiveByTarget} 는 0 을
     * 반환 → 새 신고가 다시 임계에 도달할 때까지 자동 REPORTED 재발동 차단.
     * 감사 추적은 row 그대로 보존 (audit DELETE 아님 — status 만 REVIEWED 로 전이).
     *
     * @param reviewedBy 시스템 자동 처리는 'SYSTEM' / 관리자 복귀는 admin userId
     * @return 전이된 행 수
     */
    int resolvePendingByTarget(@Param("targetType") String targetType,
                                @Param("targetId") String targetId,
                                @Param("reviewedBy") String reviewedBy,
                                @Param("reviewNote") String reviewNote);

    /**
     * 회원 대시보드 — 본인이 신고한 최신 N건. reporter_user_id = userId.
     * 관리자 list 와 동일 JOIN (target_title 포함).
     */
    List<BbsReport> findRecentByReporter(@Param("reporterUserId") String reporterUserId,
                                          @Param("limit") int limit);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
