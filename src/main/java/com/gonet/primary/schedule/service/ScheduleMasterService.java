package com.gonet.primary.schedule.service;

import com.gonet.primary.schedule.dto.ScheduleMaster;
import com.gonet.primary.schedule.dto.ScheduleMasterSaveForm;
import com.gonet.primary.schedule.dto.ScheduleMasterSearch;

import java.util.List;

/**
 * 일정 마스터(그룹) 관리 — site/menu 컨텍스트 + 그룹 헤더.
 *
 * <p>일정 detail 은 {@link ScheduleService} 가 관리. 본 서비스는 master 그룹 CRUD 만 담당.
 */
public interface ScheduleMasterService {

    List<ScheduleMaster> search(ScheduleMasterSearch search);

    int count(ScheduleMasterSearch search);

    ScheduleMaster get(String scheduleMasterId);

    String create(ScheduleMasterSaveForm form);

    void update(ScheduleMasterSaveForm form);

    void toggleUse(String scheduleMasterId, boolean active);

    void softDelete(String scheduleMasterId);
}
