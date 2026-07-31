package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.dto.MemberSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_member CRUD Mapper.
 */
@EgovMapper
public interface MemberMapper {

    // 조회
    List<Member> findList(@Param("search") MemberSearch search);
    int          countList(@Param("search") MemberSearch search);
    Member       findById(@Param("memberId") String memberId);
    Member       findByLoginId(@Param("siteId") String siteId,
                                 @Param("loginId") String loginId);
    Member       findByEmailHash(@Param("siteId") String siteId,
                                   @Param("emailHash") String emailHash);
    Member       findByLoginIdAndEmailHash(@Param("siteId") String siteId,
                                            @Param("loginId") String loginId,
                                            @Param("emailHash") String emailHash);

    /**
     * 이름(평문 member_name) + email_hash AND 매칭. /member/find-id 강화 (2026-05-25).
     * <p>member_name 은 평문 컬럼이라 SQL 단 직접 비교. trim 후 호출자가 정규화.
     */
    Member       findByMemberNameAndEmailHash(@Param("siteId") String siteId,
                                                @Param("memberName") String memberName,
                                                @Param("emailHash") String emailHash);

    int existsByLoginId(@Param("siteId") String siteId,
                         @Param("loginId") String loginId,
                         @Param("excludeMemberId") String excludeMemberId);

    int existsByEmailHash(@Param("emailHash") String emailHash,
                           @Param("excludeMemberId") String excludeMemberId);

    // CUD
    void insert(Member m);

    void updateProfile(Member m);

    void updatePassword(@Param("memberId") String memberId,
                         @Param("password") String password,
                         @Param("passwordChangedAt") LocalDateTime changedAt,
                         @Param("passwordExpireAt")  LocalDateTime expireAt);

    /** 로그인 성공 시 최종 로그인 일시/IP 및 실패 카운트 초기화. */
    void updateLastLogin(@Param("memberId") String memberId,
                          @Param("lastLoginAt") LocalDateTime lastLoginAt,
                          @Param("lastLoginIp") String lastLoginIp);

    /** 로그인 실패 카운터 +1 + threshold 도달 시 locked_until 자동 설정 (원자적). */
    int incrementLoginFailAndMaybeLock(@Param("loginId") String loginId,
                                        @Param("threshold") int threshold,
                                        @Param("lockMinutes") int lockMinutes);

    /** loginId 의 현재 login_fail_count. 없으면 0. */
    Integer findLoginFailCountByLoginId(@Param("loginId") String loginId);

    /** loginId 의 현재 locked_until. 없거나 잠금 해제 상태면 null. */
    LocalDateTime findLockedUntilByLoginId(@Param("loginId") String loginId);

    /** 관리자에 의한 강제 잠금 해제 — locked_until=NULL, login_fail_count=0. 영향 행 수 반환. */
    int unlockMember(@Param("memberId") String memberId);

    /** 5회 실패 잠금 발동/해제 시점에 captcha_required_yn 토글. 로그인 성공 시 'N' 복귀. */
    int setCaptchaRequiredByLoginId(@Param("loginId") String loginId,
                                     @Param("yn") String yn);

    /** 로그인 폼 GET 단계에서 위젯 노출 여부 결정용. 'Y' 면 강제 CAPTCHA. 없으면 null. */
    String findCaptchaRequiredByLoginId(@Param("loginId") String loginId);

    void updateStatus(@Param("memberId") String memberId,
                       @Param("status") String status);

    int softDelete(@Param("memberId") String memberId);

    // 비밀번호 이력
    void insertPasswordHistory(@Param("pwdHistoryId") String pwdHistoryId,
                                @Param("memberId") String memberId,
                                @Param("passwordHash") String passwordHash,
                                @Param("changedAt") LocalDateTime changedAt);

    /**
     * 최근 비밀번호 해시 목록(재사용 금지 검사용) — {@code changed_at DESC} 로 N건.
     * 반환된 각 해시와 신규 평문 비밀번호를 BCrypt 로 비교해 일치가 있으면 거부.
     */
    List<String> findRecentPasswordHashes(@Param("memberId") String memberId,
                                            @Param("limit") int limit);

    /**
     * 탈퇴 감사 행 INSERT.
     *
     * @param member            원 회원 행 (생성 audit 정보 + di_hash 등 보존용)
     * @param status            카테고리 코드 (USER_REQUEST / ADMIN_FORCE / DORMANT_EXPIRED)
     * @param reason            자유 텍스트 — 실제 탈퇴 사유 (관리자 강제 시 5자 이상)
     * @param retentionExpireAt 보관 만료 일시
     */
    void insertWithdraw(@Param("member") Member member,
                         @Param("status") String status,
                         @Param("reason") String reason,
                         @Param("retentionExpireAt") LocalDateTime retentionExpireAt);


    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);


    /**
     * 탈퇴 만료 영구 삭제 — {@code tb_member_withdraw.retention_expire_at &lt;= now} 인 행 hard delete.
     * {@link com.gonet.scheduler.WithdrawPurgeScheduler} 가 매일 호출.
     */
    int purgeExpiredWithdraw(@Param("now") LocalDateTime now);

    /** dry-run 카운트 — DELETE 없이 만료 후보 수만. */
    int countExpiredWithdraw(@Param("now") LocalDateTime now);
}
