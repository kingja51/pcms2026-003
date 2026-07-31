package com.gonet.primary.board.report.service;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.report.dto.BbsReport;
import com.gonet.primary.board.report.dto.BbsReportSaveForm;
import com.gonet.primary.board.report.dto.BbsReportSearch;
import com.gonet.primary.system.login.dto.CustomUserDetails;

import java.util.List;

/**
 * 게시판 신고 서비스 — article + comment 통합.
 *
 * <p>사용자 측 — {@link #report} 만 사용. 같은 사용자가 같은 대상을 다시 신고하면
 * {@link IllegalStateException} 으로 거부 (UNIQUE 제약 + 사전 검증).
 *
 * <p>관리자 측 — {@link #search} / {@link #review} 로 모더레이션 큐 처리.
 */
public interface BoardReportService {

    /**
     * 사용자 신고 적재.
     *
     * <ul>
     *   <li>중복 신고 차단 (1인 1회)</li>
     *   <li>대상의 {@code report_count} 비정규화 컬럼 동기화</li>
     *   <li>활성 신고 수가 임계치 이상이면 대상의 {@code status='REPORTED'} 자동 전환</li>
     * </ul>
     *
     * @param target ARTICLE / COMMENT
     * @param targetId 대상 ID
     * @param form 신고 사유 (reason_code 필수)
     * @param me 신고자 (인증 필수)
     */
    void report(BbsLikeTarget target, String targetId, BbsReportSaveForm form, CustomUserDetails me);

    /** 관리자 모더레이션 화면 — 페이징 목록. */
    PageResponse<BbsReport> search(BbsReportSearch search);

    /** 단건 — detail 페이지. */
    BbsReport getById(String reportId);

    /**
     * 관리자 검토 처리.
     *
     * <ul>
     *   <li>{@code status='REVIEWED'} : 콘텐츠 차단 / 삭제 처리됨 표시</li>
     *   <li>{@code status='REJECTED'} : 부적절 신고로 기각</li>
     * </ul>
     *
     * @param reviewNote 검토 메모 (선택)
     * @param admin 처리한 관리자
     */
    void review(String reportId, String status, String reviewNote, CustomUserDetails admin);

    /**
     * 회원 대시보드 — 본인이 신고한 최신 N건.
     * reporter_user_id = userId. JOIN target_title 포함.
     */
    List<BbsReport> findRecentByReporter(String userId, int limit);

    /**
     * 대상의 PENDING 신고들을 일괄 REVIEWED 로 정리.
     *
     * <p>호출 시점:
     * <ul>
     *   <li>관리자가 REPORTED → PUBLISHED 복귀시킨 직후
     *       ({@code BoardArticleServiceImpl.adminUpdateStatus} /
     *        {@code BoardCommentServiceImpl.adminUpdateStatus})</li>
     *   <li>자동 REPORTED 임계 도달 직후 ({@link #report} 내부)</li>
     * </ul>
     *
     * <p>이후 {@code countActiveByTarget} 가 0 으로 떨어져 무한 REPORTED 재전환 회귀를 방지한다.
     * 또한 비정규화 컬럼 {@code report_count} 도 0 으로 동기화한다.
     *
     * @param target ARTICLE / COMMENT
     * @param targetId 대상 ID
     * @param reviewedBy 처리자 — 관리자 userId 또는 'SYSTEM'
     * @param reviewNote 처리 사유 (선택)
     */
    void resolveOpenReportsForTarget(BbsLikeTarget target, String targetId,
                                     String reviewedBy, String reviewNote);
}
