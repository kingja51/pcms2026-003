package com.gonet.primary.survey.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_survey 엔티티 — 설문 인스턴스 (detail).
 *
 * <p>2026-04-30 master/detail 분리 — site_id / menu_id 는 {@link SurveyMaster} 로 이전.
 * 본 DTO 의 {@code siteId/menuId/siteCode/siteName} 은 master JOIN 으로 채워지는 read-only 노출 필드.
 *
 * <p>{@code questions} 는 비-엔티티 — Service 가 별도 mapper 호출로 채워 넣는다 (선택).
 */
@Getter
@Setter
public class Survey extends BaseEntity implements SoftDeletable, UseFlagged {

    private String        surveyId;
    private String        surveyMasterId;
    private String        surveyTitle;
    private String        surveyDescription;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String        status;
    private String        anonymousYn;
    private String        oneResponseYn;
    private String        useYn;
    private String        deleteYn;

    // master JOIN 으로 채워지는 노출 필드 — INSERT/UPDATE 대상 아님
    private String siteId;
    private String menuId;
    private String siteCode;
    private String siteName;
    private String layoutPath;
    private String masterTitle;

    private List<SurveyQuestion> questions;
    private long responseCount;

    public SurveyStatus statusEnum() {
        return SurveyStatus.safeParse(status);
    }

    /** 현재 시각 기준 응답 가능 여부. */
    public boolean isOpenNow() {
        if (!"PUBLISHED".equals(status)) return false;
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startAt) && !now.isAfter(endAt);
    }
}
