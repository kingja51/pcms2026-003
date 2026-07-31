package com.gonet.primary.member.service;

import com.gonet.primary.member.dto.MemberConsent;
import com.gonet.primary.member.dto.MemberConsentSearch;

import java.util.List;

/**
 * 회원 동의 이력 — 관리자 조회 전용 (read-only).
 *
 * <p>CUD 는 사용자 가입/마이페이지 흐름에서 자동 — 본 Service 는 검색·단건·엑셀만.
 */
public interface MemberConsentMngService {

    List<MemberConsent> search(MemberConsentSearch search);

    int count(MemberConsentSearch search);

    MemberConsent get(String memberConsentId);

    /** 엑셀 다운로드용 — 페이징 없이 검색 조건 전체. 최대 5만 행. */
    List<MemberConsent> findForExport(MemberConsentSearch search);
}
