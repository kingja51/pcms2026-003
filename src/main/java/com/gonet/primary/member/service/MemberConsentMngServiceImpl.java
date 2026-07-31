package com.gonet.primary.member.service;

import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.dto.MemberConsent;
import com.gonet.primary.member.dto.MemberConsentSearch;
import com.gonet.primary.member.mapper.MemberConsentMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberConsentMngServiceImpl extends EgovAbstractServiceImpl
        implements MemberConsentMngService {

    private final MemberConsentMapper mapper;

    public MemberConsentMngServiceImpl(MemberConsentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MemberConsent> search(MemberConsentSearch search) {
        return mapper.findList(search);
    }

    @Override
    public int count(MemberConsentSearch search) {
        return mapper.countList(search);
    }

    @Override
    public MemberConsent get(String memberConsentId) {
        if (memberConsentId == null || memberConsentId.isBlank()) return null;
        return mapper.findById(memberConsentId);
    }

    @Override
    public List<MemberConsent> findForExport(MemberConsentSearch search) {
        return mapper.findForExport(search);
    }
}
