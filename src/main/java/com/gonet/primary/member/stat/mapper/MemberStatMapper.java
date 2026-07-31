package com.gonet.primary.member.stat.mapper;

import com.gonet.primary.member.stat.dto.AgeBucketCount;
import com.gonet.primary.member.stat.dto.DailyMemberStat;
import com.gonet.primary.member.stat.dto.DiVerifiedShare;
import com.gonet.primary.member.stat.dto.GenderCount;
import com.gonet.primary.member.stat.dto.MemberCountByTable;
import com.gonet.primary.member.stat.dto.ProviderCount;
import com.gonet.primary.member.stat.dto.SiteMemberCount;
import com.gonet.primary.member.stat.dto.WithdrawStatusCount;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 회원 통계 read-only 매퍼.
 *
 * <p>모든 SELECT 는 delete_yn='N' 필터링 (탈퇴/휴면 테이블은 자체 PK 기준 — soft-delete 없음).
 * <p>siteId 가 null 이면 전체 사이트.
 * <p>일자 범위 (from..to) 는 inclusive — to 는 호출 측에서 endOfDay 변환 책임.
 */
@EgovMapper
public interface MemberStatMapper {

    // ──────────────────────────────────────────────────────────────────────
    // 1) 라이프사이클 3 테이블 카운트
    // ──────────────────────────────────────────────────────────────────────

    /** tb_member 활성 회원수 (delete_yn='N'). */
    long countActiveMembers(@Param("siteId") String siteId);

    /** tb_member_dormant 휴면 회원수. */
    long countDormantMembers(@Param("siteId") String siteId);

    /** tb_member_withdraw 탈퇴 회원수 (retention_expire_at 만료 전). */
    long countWithdrawnMembers(@Param("siteId") String siteId);

    /** 3 테이블을 1 쿼리(UNION ALL) 로 묶어 반환 — 화면 카드용. */
    List<MemberCountByTable> countByLifecycleTable(@Param("siteId") String siteId);

    // ──────────────────────────────────────────────────────────────────────
    // 2) 연령대 / 성별
    // ──────────────────────────────────────────────────────────────────────

    /** 연령대별 활성 회원수 — birth_year 평문 기반 동적 계산. */
    List<AgeBucketCount> ageDistribution(@Param("siteId") String siteId);

    /** 성별 활성 회원수. */
    List<GenderCount> genderDistribution(@Param("siteId") String siteId);

    // ──────────────────────────────────────────────────────────────────────
    // 3) OAuth2 / DI 인증
    // ──────────────────────────────────────────────────────────────────────

    /** provider 별 회원수 (tb_member_oauth, 활성 매핑만). */
    List<ProviderCount> providerDistribution(@Param("siteId") String siteId);

    /** di_hash 보유 vs 미보유 회원수 — 1 행. */
    DiVerifiedShare diVerifiedShare(@Param("siteId") String siteId);

    // ──────────────────────────────────────────────────────────────────────
    // 4) 탈퇴 사유 분포
    // ──────────────────────────────────────────────────────────────────────

    /** withdraw_status 별 누적 탈퇴 수 (retention_expire_at 만료 전). */
    List<WithdrawStatusCount> withdrawStatusDistribution(@Param("siteId") String siteId);

    // ──────────────────────────────────────────────────────────────────────
    // 5) 일별 추이 (가입 / 휴면 전환 / 탈퇴)
    // ──────────────────────────────────────────────────────────────────────

    /** 일별 가입 수 (tb_member.created_at). from..to inclusive. */
    List<DailyMemberStat> dailyJoinCount(@Param("siteId") String siteId,
                                          @Param("from")   LocalDate from,
                                          @Param("to")     LocalDate to);

    /** 일별 휴면 전환 수 (tb_member_dormant.created_at). */
    List<DailyMemberStat> dailyDormantCount(@Param("siteId") String siteId,
                                             @Param("from")   LocalDate from,
                                             @Param("to")     LocalDate to);

    /** 일별 탈퇴 수 (tb_member_withdraw.withdraw_at). */
    List<DailyMemberStat> dailyWithdrawCount(@Param("siteId") String siteId,
                                              @Param("from")   LocalDate from,
                                              @Param("to")     LocalDate to);

    // ──────────────────────────────────────────────────────────────────────
    // 6) 사이트별 회원 분포 — 전체 모드에서만 의미 있음
    // ──────────────────────────────────────────────────────────────────────

    /** 사이트별 활성 회원수. JOIN tb_site 로 코드/이름 동시 노출. */
    List<SiteMemberCount> memberCountBySite();
}
