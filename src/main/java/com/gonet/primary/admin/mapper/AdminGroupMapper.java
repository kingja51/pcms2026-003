package com.gonet.primary.admin.mapper;

import com.gonet.primary.admin.dto.AdminGroupOption;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

import java.util.List;

/**
 * tb_admin_group 읽기 전용 조회 (드롭다운 용도).
 *
 * <p>그룹 자체 CRUD 는 별도 Controller 로 분리 (향후 AdminGroupMngController).
 */
@EgovMapper
public interface AdminGroupMapper {

    List<AdminGroupOption> findActiveOptions();

    AdminGroupOption findById(String adminGroupId);
}
