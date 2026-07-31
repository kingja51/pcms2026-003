package com.gonet.primary.member.dormant.service;

import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.dormant.dto.DormantNotice;
import com.gonet.primary.member.dormant.dto.DormantNoticeSearch;
import com.gonet.primary.member.dormant.dto.DormantSearch;
import com.gonet.primary.member.dormant.mapper.DormantAdminMapper;
import com.gonet.primary.member.dto.Member;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class DormantAdminServiceImpl extends EgovAbstractServiceImpl
        implements DormantAdminService {

    private final DormantAdminMapper mapper;

    public DormantAdminServiceImpl(DormantAdminMapper mapper) {
        this.mapper = mapper;
    }

    @Override public List<Member> searchDormant(DormantSearch s) { return mapper.findDormantList(s); }
    @Override public int countDormant(DormantSearch s)           { return mapper.countDormantList(s); }
    @Override public Member getDormant(String id) {
        return (id == null || id.isBlank()) ? null : mapper.findDormantById(id);
    }
    @Override public List<Member> findDormantForExport(DormantSearch s) { return mapper.findDormantForExport(s); }

    @Override public List<DormantNotice> searchNotice(DormantNoticeSearch s) { return mapper.findNoticeList(s); }
    @Override public int countNotice(DormantNoticeSearch s)                  { return mapper.countNoticeList(s); }
    @Override public DormantNotice getNotice(String id) {
        return (id == null || id.isBlank()) ? null : mapper.findNoticeById(id);
    }
    @Override public List<DormantNotice> findNoticeForExport(DormantNoticeSearch s) { return mapper.findNoticeForExport(s); }
}
