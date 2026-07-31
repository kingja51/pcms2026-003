package com.gonet.primary.holiday.service;

import com.gonet.primary.holiday.dto.Holiday;
import com.gonet.primary.holiday.dto.HolidaySaveForm;
import com.gonet.primary.holiday.dto.HolidaySearch;

import java.time.LocalDate;
import java.util.List;

public interface HolidayService {

    List<Holiday> search(HolidaySearch search);

    int count(HolidaySearch search);

    Holiday get(String holidayId);

    /** 캘린더 / 사용자 화면용 — 전역 공휴일을 기간 범위로 조회. */
    List<Holiday> findInRange(LocalDate from, LocalDate to);

    String create(HolidaySaveForm form);

    void update(HolidaySaveForm form);

    void toggleUse(String holidayId, boolean active);

    void softDelete(String holidayId);
}
