package com.gonet.logging.log.mapper;

import com.gonet.logging.log.dto.LoginLog;
import com.gonet.logging.viewer.dto.LogSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * log_login INSERT + 관리자 viewer 조회 Mapper (logging DB).
 */
@EgovMapper
public interface LoginLogMapper {

    int insert(LoginLog loginLog);

    List<LoginLog> findRecent(@Param("search") LogSearch search);

    /** 페이징용 총 건수 — findRecent 와 동일한 WHERE */
    long countRecent(@Param("search") LogSearch search);

    LoginLog findById(@Param("id") long id);
}
