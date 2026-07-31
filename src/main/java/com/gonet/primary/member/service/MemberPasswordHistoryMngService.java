package com.gonet.primary.member.service;

import com.gonet.primary.member.dto.MemberPasswordHistory;
import com.gonet.primary.member.dto.MemberPasswordHistorySearch;

import java.util.List;

public interface MemberPasswordHistoryMngService {
    List<MemberPasswordHistory> search(MemberPasswordHistorySearch search);
    int count(MemberPasswordHistorySearch search);
    MemberPasswordHistory get(String pwdHistoryId);
    List<MemberPasswordHistory> findForExport(MemberPasswordHistorySearch search);
}
