package com.gonet.primary.member.dormant.service;

import com.gonet.primary.member.dormant.dto.DormantNotice;
import com.gonet.primary.member.dormant.dto.DormantNoticeSearch;
import com.gonet.primary.member.dormant.dto.DormantSearch;
import com.gonet.primary.member.dto.Member;

import java.util.List;

/**
 * 휴면 회원 + 알림 발송 이력 관리자 조회 전용.
 */
public interface DormantAdminService {

    List<Member> searchDormant(DormantSearch search);
    int countDormant(DormantSearch search);
    Member getDormant(String memberId);
    List<Member> findDormantForExport(DormantSearch search);

    List<DormantNotice> searchNotice(DormantNoticeSearch search);
    int countNotice(DormantNoticeSearch search);
    DormantNotice getNotice(String noticeId);
    List<DormantNotice> findNoticeForExport(DormantNoticeSearch search);
}
