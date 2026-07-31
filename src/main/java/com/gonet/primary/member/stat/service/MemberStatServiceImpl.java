package com.gonet.primary.member.stat.service;

import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.stat.dto.AgeBucketCount;
import com.gonet.primary.member.stat.dto.DailyMemberStat;
import com.gonet.primary.member.stat.dto.DiVerifiedShare;
import com.gonet.primary.member.stat.dto.GenderCount;
import com.gonet.primary.member.stat.dto.MemberCountByTable;
import com.gonet.primary.member.stat.dto.ProviderCount;
import com.gonet.primary.member.stat.dto.SiteMemberCount;
import com.gonet.primary.member.stat.dto.WithdrawStatusCount;
import com.gonet.primary.member.stat.mapper.MemberStatMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 회원 통계 read-only Service 구현.
 *
 * <p>모든 메서드는 read-only — 클래스 단 {@code @Transactional(readOnly=true)}.
 * <p>일별 추이는 3 SELECT 결과를 statDate 키로 머지 + 비어있는 일자 0 채움.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberStatServiceImpl extends EgovAbstractServiceImpl implements MemberStatService {

    private final MemberStatMapper mapper;

    public MemberStatServiceImpl(MemberStatMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MemberCountByTable> countByLifecycleTable(String siteId) {
        return mapper.countByLifecycleTable(siteId);
    }

    @Override
    public List<AgeBucketCount> ageDistribution(String siteId) {
        return mapper.ageDistribution(siteId);
    }

    @Override
    public List<GenderCount> genderDistribution(String siteId) {
        return mapper.genderDistribution(siteId);
    }

    @Override
    public List<ProviderCount> providerDistribution(String siteId) {
        return mapper.providerDistribution(siteId);
    }

    @Override
    public DiVerifiedShare diVerifiedShare(String siteId) {
        DiVerifiedShare r = mapper.diVerifiedShare(siteId);
        if (r == null) {
            r = new DiVerifiedShare();
            r.setVerified(0);
            r.setUnverified(0);
        }
        return r;
    }

    @Override
    public List<WithdrawStatusCount> withdrawStatusDistribution(String siteId) {
        return mapper.withdrawStatusDistribution(siteId);
    }

    @Override
    public List<DailyMemberStat> dailyTrend(String siteId, LocalDate from, LocalDate to) {
        // 3 SELECT 결과를 LocalDate 키로 합치고, from..to 모든 날짜에 0 행을 미리 만든 뒤
        // 각 시리즈 값을 채워 넣어 일관된 시간축을 제공.
        Map<LocalDate, DailyMemberStat> buckets = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyMemberStat empty = new DailyMemberStat();
            empty.setStatDate(d);
            buckets.put(d, empty);
        }

        for (DailyMemberStat row : mapper.dailyJoinCount(siteId, from, to)) {
            DailyMemberStat bucket = buckets.get(row.getStatDate());
            if (bucket != null) bucket.setJoinCount(row.getJoinCount());
        }
        for (DailyMemberStat row : mapper.dailyDormantCount(siteId, from, to)) {
            DailyMemberStat bucket = buckets.get(row.getStatDate());
            if (bucket != null) bucket.setDormantCount(row.getDormantCount());
        }
        for (DailyMemberStat row : mapper.dailyWithdrawCount(siteId, from, to)) {
            DailyMemberStat bucket = buckets.get(row.getStatDate());
            if (bucket != null) bucket.setWithdrawCount(row.getWithdrawCount());
        }

        return List.copyOf(buckets.values());
    }

    @Override
    public List<SiteMemberCount> memberCountBySite() {
        return mapper.memberCountBySite();
    }
}
