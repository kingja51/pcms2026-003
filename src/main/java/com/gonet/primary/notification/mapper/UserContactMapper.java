package com.gonet.primary.notification.mapper;

import com.gonet.primary.notification.dto.UserContact;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

/**
 * user_id (UUID v7 PK) 로 회원/직원/관리자 contact 정보 lookup.
 *
 * <p>3-way UNION ALL 으로 첫 매치 row 를 반환. 한 user_id 가 두 테이블에 동시에 있을 일은
 * 없지만, 불가피한 경우 우선순위는 ADMIN → EMPLOYEE → MEMBER (관리자 권한 우위 가정).
 */
@EgovMapper
public interface UserContactMapper {

    UserContact findByUserId(@Param("userId") String userId);
}
