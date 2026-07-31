package com.gonet.primary.schedule.service;

import com.gonet.primary.schedule.dto.Schedule;
import com.gonet.primary.schedule.dto.ScheduleSaveForm;
import com.gonet.primary.schedule.dto.ScheduleSearch;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleService {

    List<Schedule> search(ScheduleSearch search);

    int count(ScheduleSearch search);

    Schedule get(String scheduleId);

    /** 캘린더용 — 마스터(옵션) + 기간 겹침. scheduleMasterId 가 null/blank 면 전체 마스터 조회. */
    List<Schedule> findOverlapping(String scheduleMasterId, LocalDateTime rangeStart, LocalDateTime rangeEnd);

    String create(ScheduleSaveForm form);

    void update(ScheduleSaveForm form);

    void toggleUse(String scheduleId, boolean active);

    void softDelete(String scheduleId);
}
