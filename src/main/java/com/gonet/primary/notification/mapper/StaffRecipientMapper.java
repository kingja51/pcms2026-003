package com.gonet.primary.notification.mapper;

import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * "STAFF 이상" 활성 직원/관리자 user_id 목록 lookup.
 *
 * <p>운영 broadcast 알림용 (휴면 전환 / 파일 INFECTED 등). v_user_login 의 role_ids
 * 는 closure 확장 결과라 STAFF 위치보다 위 권한자 (MANAGER/ADMIN/SUPER) 모두 STAFF role_id
 * 를 CSV 에 포함한다 — FIND_IN_SET 한 번으로 "STAFF 이상" 매치.
 *
 * <p>제외:
 * <ul>
 *   <li>회원(MEMBER) — 운영 알림 대상 아님</li>
 *   <li>비활성/잠금 상태 (status != ACTIVE)</li>
 *   <li>탈퇴/퇴사 (delete_yn = 'Y')</li>
 *   <li>role_ids 가 null 인 직원/관리자 (역할 미배정)</li>
 * </ul>
 */
@EgovMapper
public interface StaffRecipientMapper {

    /**
     * 입력 role_code (예: "ROLE_STAFF") 를 가진 활성 admin/employee 의 user_id 목록.
     * tb_role 의 동일 role_code 가 여러 site_id 로 존재할 수 있어 EXISTS 로 묶음.
     */
    List<String> findActiveUserIdsByRoleCode(@Param("roleCode") String roleCode);
}
