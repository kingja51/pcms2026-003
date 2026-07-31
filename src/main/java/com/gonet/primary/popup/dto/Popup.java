package com.gonet.primary.popup.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * tb_popup 엔티티 — 사이트별 팝업 (LAYER/MODAL/WINDOW/BANNER).
 *
 * <p>노출 조건은 다음 4축의 AND 결합으로 결정:
 * <ol>
 *   <li>{@code use_yn = 'Y'}</li>
 *   <li>{@code show_from <= now <= show_to}</li>
 *   <li>{@code show_days} 가 비어있거나 현재 요일 포함 (예: "MON,TUE")</li>
 *   <li>{@code show_time_from / show_time_to} 가 둘 다 비어있거나 현재 시각이 범위 안</li>
 * </ol>
 *
 * <p>표시용 JOIN 필드: {@code siteCode}, {@code siteName}.
 */
@Getter
@Setter
public class Popup extends BaseEntity implements SoftDeletable, UseFlagged {

    private String popupId;
    private String siteId;
    private String popupTitle;
    private String popupContent;
    private String popupType;

    private String linkUrl;
    /** 첨부 file_group_id (FG0_…) — popup 본문 이미지 업로드 그룹. */
    private String fileGroupId;

    private Integer widthPx;
    private Integer heightPx;
    private Integer positionX;
    private Integer positionY;

    private LocalDateTime showFrom;
    private LocalDateTime showTo;
    private String        showDays;
    private LocalTime     showTimeFrom;
    private LocalTime     showTimeTo;

    private int cookieDays;
    private int sortOrder;

    private String useYn;
    private String deleteYn;

    private String siteCode;
    private String siteName;

    public PopupType popupTypeEnum() {
        return PopupType.safeParse(popupType);
    }
}
