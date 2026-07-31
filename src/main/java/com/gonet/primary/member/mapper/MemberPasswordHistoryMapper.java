package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.MemberPasswordHistory;
import com.gonet.primary.member.dto.MemberPasswordHistorySearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_member_password_history — 관리자 조회 전용.
 *
 * <p>CUD 는 회원 가입/비번 변경 흐름에서 자동 (MemberMapper 안의 INSERT) — 본 mapper 는 read-only.
 */
@EgovMapper
public interface MemberPasswordHistoryMapper {

    List<MemberPasswordHistory> findList(@Param("search") MemberPasswordHistorySearch search);

    int countList(@Param("search") MemberPasswordHistorySearch search);

    MemberPasswordHistory findById(@Param("pwdHistoryId") String pwdHistoryId);

    List<MemberPasswordHistory> findForExport(@Param("search") MemberPasswordHistorySearch search);
}
