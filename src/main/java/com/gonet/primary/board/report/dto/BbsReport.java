package com.gonet.primary.board.report.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_bbs_report — 게시판 신고 (article + comment 통합).
 *
 * <p>UNIQUE (target_type, target_id, reporter_user_id) — 1인 1회 신고.
 * 처리 상태:
 * <ul>
 *   <li>{@code PENDING}  — 신규 접수, 관리자 검토 대기</li>
 *   <li>{@code REVIEWED} — 검토 완료, 콘텐츠는 차단/삭제됨</li>
 *   <li>{@code REJECTED} — 검토 결과 부적절 신고로 기각</li>
 * </ul>
 *
 * <p>JOIN 조회 시 함께 노출되는 필드:
 * <ul>
 *   <li>{@code targetTitle} — article 의 title 또는 comment 의 content 앞부분</li>
 *   <li>{@code reporterLoginId} — 화면 노출용 (신고자 식별)</li>
 * </ul>
 */
@Getter
@Setter
public class BbsReport extends BaseEntity implements SoftDeletable {

    private String        reportId;
    private String        targetType;          // ARTICLE / COMMENT
    private String        targetId;
    private String        reporterUserId;
    private String        reporterUserType;    // MEMBER / EMPLOYEE / ADMIN
    private String        sourceUrl;           // 신고가 접수된 페이지 URL — 감사/추적 용
    private String        reasonCode;          // SPAM / OFFENSIVE / ILLEGAL / COPYRIGHT / PRIVACY / OTHER
    private String        reasonText;
    private String        status;              // PENDING / REVIEWED / REJECTED
    private String        reviewedBy;
    private LocalDateTime reviewedAt;
    private String        reviewNote;
    private String        deleteYn;

    // JOIN 필드 (목록 조회용)
    private String        targetTitle;         // 신고된 콘텐츠 미리보기
    private String        reporterLoginId;     // 신고자 식별
}
