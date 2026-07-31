package com.gonet.primary.member.oauth2.service;

import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.oauth2.dto.MemberOAuth;
import com.gonet.primary.member.oauth2.dto.MemberOAuthSearch;
import com.gonet.primary.member.oauth2.mapper.MemberOAuthMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberOAuthMngServiceImpl extends EgovAbstractServiceImpl
        implements MemberOAuthMngService {

    private final MemberOAuthMapper mapper;

    public MemberOAuthMngServiceImpl(MemberOAuthMapper mapper) {
        this.mapper = mapper;
    }

    @Override public List<MemberOAuth> search(MemberOAuthSearch s) { return mapper.findList(s); }
    @Override public int count(MemberOAuthSearch s)                { return mapper.countList(s); }
    @Override public MemberOAuth get(String id) {
        return (id == null || id.isBlank()) ? null : mapper.findById(id);
    }
    @Override public List<MemberOAuth> findForExport(MemberOAuthSearch s) {
        return mapper.findForExport(s);
    }
}
