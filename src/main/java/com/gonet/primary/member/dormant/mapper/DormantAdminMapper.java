package com.gonet.primary.member.dormant.mapper;

import com.gonet.primary.member.dormant.dto.DormantNotice;
import com.gonet.primary.member.dormant.dto.DormantNoticeSearch;
import com.gonet.primary.member.dormant.dto.DormantSearch;
import com.gonet.primary.member.dto.Member;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 휴면 회원 + 휴면 안내 발송 이력 관리자 조회.
 *
 * <p>휴면 회원 라이프사이클(전환/복원/파기)은 기존 {@link DormantMapper} 가 담당.
 * 본 mapper 는 관리자 화면 검색·엑셀 전용.
 */
@EgovMapper
public interface DormantAdminMapper {

    // tb_member_dormant — 휴면 회원 목록
    List<Member> findDormantList(@Param("search") DormantSearch search);

    int countDormantList(@Param("search") DormantSearch search);

    Member findDormantById(@Param("memberId") String memberId);

    List<Member> findDormantForExport(@Param("search") DormantSearch search);

    // tb_member_dormant_notice — 알림 발송 이력
    List<DormantNotice> findNoticeList(@Param("search") DormantNoticeSearch search);

    int countNoticeList(@Param("search") DormantNoticeSearch search);

    DormantNotice findNoticeById(@Param("noticeId") String noticeId);

    List<DormantNotice> findNoticeForExport(@Param("search") DormantNoticeSearch search);
}
