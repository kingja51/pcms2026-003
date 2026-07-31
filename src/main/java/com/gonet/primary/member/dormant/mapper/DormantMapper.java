package com.gonet.primary.member.dormant.mapper;

import com.gonet.primary.member.dormant.dto.DormantCandidate;
import com.gonet.primary.member.dto.Member;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 회원 휴면 라이프사이클 — 알림 이력 · 전환 · 복원 전용.
 *
 * <p>엔티티 이동 트랜잭션:
 * <ul>
 *   <li>전환: tb_member → tb_member_dormant (INSERT ... SELECT + DELETE)</li>
 *   <li>복원: tb_member_dormant → tb_member (INSERT ... SELECT + DELETE)</li>
 * </ul>
 * <p>FK CASCADE 로 {@code tb_member_dormant_notice} 는 전환 시 자동 정리됨.
 */
@EgovMapper
public interface DormantMapper {

    // ------------------------------------------------------------------
    // 후보 조회 — 특정 단계(30D/7D/1D) 에 해당하고 아직 이력이 없는 회원
    // ------------------------------------------------------------------

    /**
     * {@code last_login_at <= now - cutoffDays} 인 회원 중, 해당 {@code stage} 이력이 없는 후보.
     * stage ∈ (30D, 7D, 1D).
     * ACTIVE 상태만 (LOCKED/SUSPENDED 는 알림 무의미).
     */
    List<DormantCandidate> findNoticeCandidates(@Param("cutoffDays") int cutoffDays,
                                                  @Param("stage") String stage);

    /** 전환 대상 — last_login_at <= now - 365d, ACTIVE. */
    List<DormantCandidate> findTransferCandidates(@Param("cutoffDays") int cutoffDays);

    // ------------------------------------------------------------------
    // 알림 이력
    // ------------------------------------------------------------------

    /** 이력 INSERT — UNIQUE(member_id,stage) 위반 시 DuplicateKeyException. */
    void insertNotice(@Param("memberId") String memberId,
                       @Param("stage") String stage);

    /** 로그인 성공 시 이력 전체 정리 — 다음 사이클 초기화. */
    int deleteNoticesByMemberId(@Param("memberId") String memberId);

    // ------------------------------------------------------------------
    // 전환 (tb_member → tb_member_dormant)
    // ------------------------------------------------------------------

    /** tb_member_dormant 로 INSERT (SELECT FROM tb_member). dormant_at=NOW, dormant_reason='INACTIVE_1Y'. */
    int insertDormantFromMember(@Param("memberId") String memberId,
                                  @Param("dormantReason") String dormantReason);

    /** tb_member 에서 해당 회원 제거 (hard delete — 이동 완료 후). */
    int deleteMember(@Param("memberId") String memberId);

    // ------------------------------------------------------------------
    // 복원 (tb_member_dormant → tb_member)
    // ------------------------------------------------------------------

    /**
     * 로그인 ID + 이메일 해시로 휴면 회원 조회 — 복원 재인증용.
     * 반환 타입은 {@link Member} — 이후 BCrypt 및 이름 검증에 필요한 필드 포함.
     */
    Member findDormantByLoginIdAndEmailHash(@Param("loginId") String loginId,
                                              @Param("emailHash") String emailHash);

    /** tb_member_dormant 단일 조회 (member_id). */
    Member findDormantById(@Param("memberId") String memberId);
    /**
     * 로그인ID + DI 해시로 휴면 스냅샷 조회 — 실명인증 경로(수단 A).
     *
     * <p>DI 는 개인 식별값이라 그것만으로도 사람을 특정할 수 있다. 그래도 로그인ID 를
     * 함께 요구하는 이유는 <b>한 사람이 여러 계정을 가질 수 있기</b> 때문이다 —
     * DI 만으로 조회하면 어느 계정을 풀지 정할 수 없다.
     */
    Member findDormantByLoginIdAndDiHash(@Param("loginId") String loginId,
                                          @Param("diHash") String diHash);

    /** 휴면 → 활성 이관. status='ACTIVE', last_login_at=NOW 로 재설정해 INSERT. */
    int insertMemberFromDormant(@Param("memberId") String memberId);

    /** tb_member_dormant 에서 제거 (이관 완료 후). */
    int deleteDormant(@Param("memberId") String memberId);

    // ------------------------------------------------------------------
    // 5년 만기 파기 (PIPA §21 — 보관 기간 만료 시 지체 없이 파기)
    // ------------------------------------------------------------------

    /**
     * 휴면 후 N년 경과한 row 의 member_id 만 조회 — 파기 후보.
     * {@code dormant_at <= now - retentionDays} 조건.
     * 본 메서드 자체는 row 를 수정하지 않으며, batch worker 가 단건 트랜잭션으로
     * {@link #hardDeleteDormant(String)} 호출하여 실제 파기.
     */
    List<String> findExpiredDormantIds(@Param("retentionDays") int retentionDays,
                                         @Param("limit") int limit);

    /**
     * tb_member_dormant 의 row 를 HARD DELETE — PIPA §21 의 "지체 없이 파기".
     * NOT NULL 처리 (anonymize) 가 아닌 row 자체 제거 — 권장 정책.
     * FK CASCADE 로 {@code tb_member_dormant_notice} 는 자동 정리.
     *
     * @return 영향 row 수 (1 = 파기 성공, 0 = 이미 다른 트랜잭션에서 처리됨)
     */
    int hardDeleteDormant(@Param("memberId") String memberId);
}
