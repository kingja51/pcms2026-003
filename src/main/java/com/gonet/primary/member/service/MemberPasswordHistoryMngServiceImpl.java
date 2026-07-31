package com.gonet.primary.member.service;

import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.dto.MemberPasswordHistory;
import com.gonet.primary.member.dto.MemberPasswordHistorySearch;
import com.gonet.primary.member.mapper.MemberPasswordHistoryMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberPasswordHistoryMngServiceImpl extends EgovAbstractServiceImpl
        implements MemberPasswordHistoryMngService {

    private final MemberPasswordHistoryMapper mapper;

    public MemberPasswordHistoryMngServiceImpl(MemberPasswordHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override public List<MemberPasswordHistory> search(MemberPasswordHistorySearch s) { return mapper.findList(s); }
    @Override public int count(MemberPasswordHistorySearch s)                          { return mapper.countList(s); }
    @Override public MemberPasswordHistory get(String id) {
        return (id == null || id.isBlank()) ? null : mapper.findById(id);
    }
    @Override public List<MemberPasswordHistory> findForExport(MemberPasswordHistorySearch s) {
        return mapper.findForExport(s);
    }
}
