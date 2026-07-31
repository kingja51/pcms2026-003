package com.gonet.primary.holiday.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class HolidaySearch extends PageRequest {

    private Integer   year;
    private String    holidayType;
    private String    useYn;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
