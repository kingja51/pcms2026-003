package com.gonet.primary.schedule.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ScheduleSearch extends PageRequest {

    private String    scheduleMasterId;  // 일정 마스터 스코프 — URL path 에서 자동 주입
    private String    scheduleCategory;
    private String    useYn;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer   year;
}
