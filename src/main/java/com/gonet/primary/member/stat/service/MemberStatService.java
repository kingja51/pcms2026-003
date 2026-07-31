package com.gonet.primary.member.stat.service;

import com.gonet.primary.member.stat.dto.AgeBucketCount;
import com.gonet.primary.member.stat.dto.DailyMemberStat;
import com.gonet.primary.member.stat.dto.DiVerifiedShare;
import com.gonet.primary.member.stat.dto.GenderCount;
import com.gonet.primary.member.stat.dto.MemberCountByTable;
import com.gonet.primary.member.stat.dto.ProviderCount;
import com.gonet.primary.member.stat.dto.SiteMemberCount;
import com.gonet.primary.member.stat.dto.WithdrawStatusCount;

import java.time.LocalDate;
import java.util.List;

/**
 * 회원 통계 read-only Service.
 *
 * <p>모든 메서드는 siteId nullable — null 이면 전체 사이트.
 */
public interface MemberStatService {

    List<MemberCountByTable>  countByLifecycleTable(String siteId);

    List<AgeBucketCount>      ageDistribution(String siteId);

    List<GenderCount>         genderDistribution(String siteId);

    List<ProviderCount>       providerDistribution(String siteId);

    DiVerifiedShare           diVerifiedShare(String siteId);

    List<WithdrawStatusCount> withdrawStatusDistribution(String siteId);

    /**
     * 기간 내 일별 변동 추이 — 가입/휴면/탈퇴 3 시리즈를 statDate 키로 합쳐 반환.
     *
     * <p>날짜가 비어 있는 일자는 자동으로 0 채움.
     */
    List<DailyMemberStat>     dailyTrend(String siteId, LocalDate from, LocalDate to);

    List<SiteMemberCount>     memberCountBySite();
}
