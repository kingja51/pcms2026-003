package com.gonet.primary.board.report.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 신고 모더레이션 화면 검색 조건.
 *
 * <p>필터:
 * <ul>
 *   <li>{@code status}     — PENDING / REVIEWED / REJECTED / ALL (default PENDING)</li>
 *   <li>{@code targetType} — ARTICLE / COMMENT / ALL</li>
 *   <li>{@code reasonCode} — SPAM / OFFENSIVE / ... / ALL</li>
 *   <li>{@code keyword}    — 신고 사유 본문 / 신고자 loginId 부분 일치 (PageRequest 상속)</li>
 * </ul>
 */
@Getter
@Setter
public class BbsReportSearch extends PageRequest {

    private String status     = "PENDING";
    private String targetType = "ALL";
    private String reasonCode = "ALL";
}
