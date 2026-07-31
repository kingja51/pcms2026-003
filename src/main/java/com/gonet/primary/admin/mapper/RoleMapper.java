package com.gonet.primary.admin.mapper;

import com.gonet.primary.admin.dto.RoleOption;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

import java.util.List;

/**
 * tb_role 읽기 전용 조회 (관리자 폼 역할 체크박스 용도).
 *
 * <p>역할 자체 CRUD 는 별도 컨트롤러(RoleMngController — S1)에서 관리.
 */
@EgovMapper
public interface RoleMapper {

    List<RoleOption> findActiveOptions();
}
