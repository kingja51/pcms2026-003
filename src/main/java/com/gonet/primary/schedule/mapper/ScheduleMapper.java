package com.gonet.primary.schedule.mapper;

import com.gonet.primary.schedule.dto.Schedule;
import com.gonet.primary.schedule.dto.ScheduleSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@EgovMapper
public interface ScheduleMapper {

    List<Schedule> findList(@Param("search") ScheduleSearch search);

    int countList(@Param("search") ScheduleSearch search);

    Schedule findById(@Param("scheduleId") String scheduleId);

    /**
     * 캘린더 / 사용자 화면용 — 주어진 기간과 겹치는 활성 일정.
     * 겹침 조건: schedule.start_at <= rangeEnd AND schedule.end_at >= rangeStart.
     */
    List<Schedule> findOverlapping(@Param("scheduleMasterId") String scheduleMasterId,
                                    @Param("rangeStart") LocalDateTime rangeStart,
                                    @Param("rangeEnd") LocalDateTime rangeEnd);

    int insert(Schedule schedule);

    int update(Schedule schedule);

    int updateUseYn(@Param("scheduleId") String scheduleId,
                     @Param("useYn") String useYn);

    int softDelete(@Param("scheduleId") String scheduleId);

    /**
     * 색인 재구축용 — 활성 일정 전체. master JOIN 으로 site_id/site_code/menu_id 노출.
     * Service 가 use_yn='Y' 만 색인.
     */
    List<Schedule> findAllForReindex();

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
