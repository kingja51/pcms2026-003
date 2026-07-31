package com.gonet.primary.holiday.mapper;

import com.gonet.primary.holiday.dto.Holiday;
import com.gonet.primary.holiday.dto.HolidaySearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.time.LocalDate;
import java.util.List;

@EgovMapper
public interface HolidayMapper {

    List<Holiday> findList(@Param("search") HolidaySearch search);

    int countList(@Param("search") HolidaySearch search);

    Holiday findById(@Param("holidayId") String holidayId);

    /** 캘린더 / 사용자 화면용 — 주어진 기간에 포함되는 활성 공휴일 (전역). */
    List<Holiday> findInRange(@Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    int existsByDate(@Param("holidayDate") LocalDate holidayDate,
                      @Param("holidayName") String holidayName,
                      @Param("excludeId") String excludeId);

    int insert(Holiday holiday);

    int update(Holiday holiday);

    int updateUseYn(@Param("holidayId") String holidayId,
                     @Param("useYn") String useYn);

    int softDelete(@Param("holidayId") String holidayId);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
