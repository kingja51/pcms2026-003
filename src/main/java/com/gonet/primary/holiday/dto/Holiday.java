package com.gonet.primary.holiday.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * tb_holiday 엔티티 — 전역 공휴일 (사이트 무관).
 *
 * <p>UNIQUE (year, holiday_date, holiday_name) — 같은 날짜에 다른 명칭 다중 등록 가능
 * (예: 추석 + 대체공휴일).
 */
@Getter
@Setter
public class Holiday extends BaseEntity implements SoftDeletable, UseFlagged {

    private String    holidayId;
    private Integer   year;
    private LocalDate holidayDate;
    private String    holidayName;
    private String    holidayType;
    private String    description;
    private String    useYn;
    private String    deleteYn;
}
