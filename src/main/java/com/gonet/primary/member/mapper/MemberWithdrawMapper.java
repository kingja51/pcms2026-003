package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.MemberWithdraw;
import com.gonet.primary.member.dto.MemberWithdrawSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_member_withdraw — 관리자 조회 전용.
 *
 * <p>탈퇴 처리/만료 영구 삭제는 별 흐름 (MemberMapper.insertWithdraw / WithdrawPurgeScheduler).
 * 본 mapper 는 검색·단건·엑셀 read-only.
 */
@EgovMapper
public interface MemberWithdrawMapper {

    List<MemberWithdraw> findList(@Param("search") MemberWithdrawSearch search);

    int countList(@Param("search") MemberWithdrawSearch search);

    MemberWithdraw findById(@Param("memberId") String memberId);

    List<MemberWithdraw> findForExport(@Param("search") MemberWithdrawSearch search);
}
