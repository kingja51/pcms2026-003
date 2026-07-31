package com.gonet.primary.member.otp.mapper;

import com.gonet.primary.member.otp.dto.MemberOtp;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

import java.time.LocalDateTime;

/** {@code tb_member_otp} 매퍼. */
@EgovMapper("memberOtpMapper")
public interface MemberOtpMapper {

    void insert(MemberOtp otp);

    /**
     * 대상·용도의 <b>가장 최근 미소비 코드</b> 1건. 여러 건이 살아 있어도
     * 마지막 발급분만 유효하다 — 재발송하면 이전 코드는 자동으로 무의미해진다.
     */
    MemberOtp findLatestActive(@Param("memberId") String memberId,
                               @Param("purpose") String purpose);

    /** 마지막 발급 시각 — 재발송 쿨다운 판정용. 없으면 {@code null}. */
    LocalDateTime findLastIssuedAt(@Param("memberId") String memberId,
                                   @Param("purpose") String purpose);

    /** 최근 1시간 발급 건수 — 시간당 상한 판정용. */
    int countIssuedSince(@Param("memberId") String memberId,
                         @Param("purpose") String purpose,
                         @Param("since") LocalDateTime since);

    /** 시도 횟수 +1. 반환값은 갱신 행 수. */
    int incrementAttempt(@Param("otpId") String otpId);

    /**
     * 검증 성공 표시 — <b>{@code verified_at IS NULL} 인 행만</b> 갱신한다.
     * 조건을 WHERE 에 두는 것이 핵심이다: 같은 코드로 동시에 두 요청이 들어와도
     * DB 가 한 번만 갱신하므로 <b>재사용이 원천 차단</b>된다. 자바 쪽 if 검사만으로는
     * 두 스레드가 동시에 통과할 수 있다.
     *
     * @return 1 이면 이번 호출이 소비에 성공, 0 이면 이미 소비됨
     */
    int markVerified(@Param("otpId") String otpId,
                     @Param("verifiedAt") LocalDateTime verifiedAt);

    /** 코드 폐기 — 시도 상한 초과 시. 물리 삭제(평문도 해시도 남길 이유가 없다). */
    int deleteById(@Param("otpId") String otpId);

    /** 대상·용도의 미소비 코드 전량 폐기 — 새 코드 발급 전에 정리. */
    int deleteActiveByMember(@Param("memberId") String memberId,
                             @Param("purpose") String purpose);

    /** 만료·사용완료 정리 — P7 보존 배치가 호출한다. */
    int deleteExpiredBefore(@Param("threshold") LocalDateTime threshold);

    /**
     * 위 삭제 대상 건수 — dry-run 미리보기용.
     *
     * <p>{@link #deleteExpiredBefore} 와 <b>같은 술어</b>를 써야 한다. 갈리면
     * 미리보기 건수와 실제 삭제 건수가 달라져 dry-run 이 무의미해진다.
     */
    int countExpiredBefore(@Param("threshold") LocalDateTime threshold);
}
