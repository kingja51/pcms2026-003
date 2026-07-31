package com.gonet.primary.member.service;

import com.gonet.primary.member.dto.AgeGroupStat;

import java.util.List;

/**
 * 회원 연령대 통계 — 관리자 화면 전용 (read-only).
 */
public interface MemberAgeStatService {

    /** 활성 회원만. siteId blank 면 전체. */
    List<AgeGroupStat> activeByAgeGroup(String siteId);

    /** 활성 + 휴면 합산. */
    List<AgeGroupStat> activeAndDormantByAgeGroup(String siteId);
}
