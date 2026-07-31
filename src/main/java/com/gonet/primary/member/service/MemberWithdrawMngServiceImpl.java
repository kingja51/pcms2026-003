package com.gonet.primary.member.service;

import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.dto.MemberWithdraw;
import com.gonet.primary.member.dto.MemberWithdrawSearch;
import com.gonet.primary.member.mapper.MemberWithdrawMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberWithdrawMngServiceImpl extends EgovAbstractServiceImpl
        implements MemberWithdrawMngService {

    private final MemberWithdrawMapper mapper;

    public MemberWithdrawMngServiceImpl(MemberWithdrawMapper mapper) {
        this.mapper = mapper;
    }

    @Override public List<MemberWithdraw> search(MemberWithdrawSearch s) { return mapper.findList(s); }
    @Override public int count(MemberWithdrawSearch s)                   { return mapper.countList(s); }
    @Override public MemberWithdraw get(String id) {
        return (id == null || id.isBlank()) ? null : mapper.findById(id);
    }
    @Override public List<MemberWithdraw> findForExport(MemberWithdrawSearch s) {
        return mapper.findForExport(s);
    }
}
