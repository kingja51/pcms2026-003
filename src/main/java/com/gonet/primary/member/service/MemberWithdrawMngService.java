package com.gonet.primary.member.service;

import com.gonet.primary.member.dto.MemberWithdraw;
import com.gonet.primary.member.dto.MemberWithdrawSearch;

import java.util.List;

public interface MemberWithdrawMngService {
    List<MemberWithdraw> search(MemberWithdrawSearch search);
    int count(MemberWithdrawSearch search);
    MemberWithdraw get(String memberId);
    List<MemberWithdraw> findForExport(MemberWithdrawSearch search);
}
