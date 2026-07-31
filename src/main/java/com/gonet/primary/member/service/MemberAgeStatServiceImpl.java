package com.gonet.primary.member.service;

import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.dto.AgeGroupStat;
import com.gonet.primary.member.mapper.MemberAgeStatMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberAgeStatServiceImpl extends EgovAbstractServiceImpl
        implements MemberAgeStatService {

    private final MemberAgeStatMapper mapper;

    public MemberAgeStatServiceImpl(MemberAgeStatMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AgeGroupStat> activeByAgeGroup(String siteId) {
        return mapper.activeByAgeGroup(blank(siteId));
    }

    @Override
    public List<AgeGroupStat> activeAndDormantByAgeGroup(String siteId) {
        return mapper.activeAndDormantByAgeGroup(blank(siteId));
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
