package com.gonet.primary.system.access.mapper;

import com.gonet.primary.system.access.dto.RoleUrlAccessRule;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

import java.util.List;

/**
 * tb_role_url_access 조회 Mapper.
 *
 * <p>S0 범위: 활성 규칙 전체 로드 (Caffeine {@code roleUrlAccess} 캐시, 30분 TTL).
 * <p>S1+: 사이트별 조회/페이지네이션/CUD 확장.
 */
@EgovMapper
public interface RoleUrlAccessMapper {

    /** use_yn='Y' AND delete_yn='N' 전체 규칙을 priority ASC 로 반환 */
    List<RoleUrlAccessRule> findAllActive();
}
