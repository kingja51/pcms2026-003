package com.gonet.primary.schedule.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_schedule 엔티티 — 개별 일정 인스턴스 (detail).
 *
 * <p>{@code allDayYn='Y'} 인 경우 시각은 무시 — 캘린더 렌더링 시 종일 표시.
 *
 * <p>2026-04-29 master/detail 분리 — site_id / menu_id 는 {@link ScheduleMaster} 로 이전.
 * 본 DTO 의 {@code siteId/menuId/siteCode/siteName} 은 master JOIN 으로 채워지는 read-only 노출 필드.
 */
@Getter
@Setter
public class Schedule extends BaseEntity implements SoftDeletable, UseFlagged {

    private String        scheduleId;
    private String        scheduleMasterId;
    private String        scheduleTitle;
    private String        scheduleContent;
    private String        scheduleCategory;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String        location;
    private String        linkUrl;
    private String        allDayYn;
    private String        colorCode;
    private String        useYn;
    private String        deleteYn;

    // master JOIN 으로 채워지는 노출 필드 — INSERT/UPDATE 대상 아님
    private String siteId;
    private String menuId;
    private String siteCode;
    private String siteName;
    private String masterTitle;
}
