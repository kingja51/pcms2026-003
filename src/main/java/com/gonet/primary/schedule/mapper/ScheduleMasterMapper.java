package com.gonet.primary.schedule.mapper;

import com.gonet.primary.schedule.dto.ScheduleMaster;
import com.gonet.primary.schedule.dto.ScheduleMasterSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@EgovMapper
public interface ScheduleMasterMapper {

    List<ScheduleMaster> findList(@Param("search") ScheduleMasterSearch search);

    int countList(@Param("search") ScheduleMasterSearch search);

    ScheduleMaster findById(@Param("scheduleMasterId") String scheduleMasterId);

    int insert(ScheduleMaster master);

    int update(ScheduleMaster master);

    /** master_title / master_content 만 갱신 — detail 의 schedule_title 변경 시 sync 용. */
    int updateTitle(@Param("scheduleMasterId") String scheduleMasterId,
                    @Param("masterTitle") String masterTitle,
                    @Param("updatedBy") String updatedBy,
                    @Param("updatedIp") String updatedIp);

    int updateUseYn(@Param("scheduleMasterId") String scheduleMasterId,
                    @Param("useYn") String useYn);

    int softDelete(@Param("scheduleMasterId") String scheduleMasterId);
}
