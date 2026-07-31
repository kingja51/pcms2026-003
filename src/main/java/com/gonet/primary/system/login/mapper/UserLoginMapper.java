package com.gonet.primary.system.login.mapper;

import com.gonet.primary.system.login.dto.UserLogin;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code v_user_login} 통합 VIEW 조회 Mapper.
 *
 * <p>로그인 폼이 회원/관리자로 분리되었으므로 user_type 필터링 버전을 제공한다:
 * <ul>
 *   <li>{@link #findByLoginId(String)} : 모든 user_type — 관리자 우선 LIMIT 1</li>
 *   <li>{@link #findByLoginIdInTypes(String, List)} : user_type 화이트리스트
 *       (member 폼 → MEMBER, admin 폼 → ADMIN/EMPLOYEE)</li>
 * </ul>
 */
@EgovMapper
public interface UserLoginMapper {

    UserLogin findByLoginId(@Param("loginId") String loginId);

    UserLogin findByLoginIdInTypes(@Param("loginId") String loginId,
                                    @Param("userTypes") List<String> userTypes);
}
