package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.MemberConsent;
import com.gonet.primary.member.dto.MemberConsentSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_member_consent — 회원 약관 동의 이력 기록 + 관리자 조회.
 */
@EgovMapper
public interface MemberConsentMapper {

    List<MemberConsent> findByMemberId(@Param("memberId") String memberId);

    void insert(MemberConsent row);

    // ------------------------------------------------------------------
    // 관리자 조회 — 목록/카운트/단건/엑셀
    // ------------------------------------------------------------------

    List<MemberConsent> findList(@Param("search") MemberConsentSearch search);

    int countList(@Param("search") MemberConsentSearch search);

    MemberConsent findById(@Param("memberConsentId") String memberConsentId);

    /** 엑셀 다운로드용 — 페이징 없이 검색 조건 전체. 상한은 호출 측에서 강제. */
    List<MemberConsent> findForExport(@Param("search") MemberConsentSearch search);
}
