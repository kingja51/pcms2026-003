package com.gonet.member;

import com.gonet.common.crypto.TokenHasher;
import com.gonet.primary.member.otp.config.OtpProperties;
import com.gonet.primary.member.otp.dto.MemberOtp;
import com.gonet.primary.member.otp.dto.OtpPurpose;
import com.gonet.primary.member.otp.mapper.MemberOtpMapper;
import com.gonet.primary.member.otp.service.MemberOtpServiceImpl;
import com.gonet.primary.member.otp.service.OtpThrottledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OTP 부정 시나리오 차단 검증 — PLAN P5 DoD.
 *
 * <p>DoD 가 요구하는 것: 만료 거부 · <b>사용 완료 재사용 거부</b> · 시도 5회 초과 폐기 ·
 * 재발송 쿨다운 · <b>평문 미보관</b>. 전부 DB 없이 확인할 수 있다 — 매퍼를 mock 으로
 * 두고 서비스가 <b>무엇을 저장하고 무엇을 거부하는지</b>를 본다.
 *
 * <p>실제 DB 왕복(동시 요청 시 markVerified 가 한 번만 1을 반환하는지)은 여기서
 * 검증하지 않는다 — 그건 SQL 의 {@code WHERE verified_at IS NULL} 이 보장하며
 * 기동 검증 항목이다.
 *
 * <p>{@link TokenHasher} 는 mock 이 아니라 실물을 쓴다. 해시가 결정적이어야
 * "저장된 해시 ≠ 평문" 을 진짜로 확인할 수 있다.
 */
class MemberOtpServiceTest {

    private static final String MEMBER_ID = "MBR_0000000000000000000000000000000001";
    private static final OtpPurpose PURPOSE = OtpPurpose.DORMANT_RESTORE;

    private MemberOtpMapper mapper;
    private OtpProperties props;
    private TokenHasher hasher;
    private MemberOtpServiceImpl service;
    private List<MemberOtp> inserted;

    @BeforeEach
    void setUp() {
        mapper = mock(MemberOtpMapper.class);
        hasher = new TokenHasher(TestKeys.piiProps());
        props = new OtpProperties();          // 기본값 = 운영 기본 정책
        service = new MemberOtpServiceImpl(mapper, props, hasher);

        inserted = new ArrayList<>();
        doAnswer(inv -> { inserted.add(inv.getArgument(0)); return null; })
            .when(mapper).insert(any(MemberOtp.class));
    }

    // ── 발급 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB 에는 평문 코드가 남지 않는다 — code_hash 만 저장한다")
    void storesHashNotPlaintext() {
        String code = service.issue(MEMBER_ID, null, PURPOSE, "127.0.0.1");

        assertThat(inserted).hasSize(1);
        MemberOtp saved = inserted.get(0);
        assertThat(saved.getCodeHash())
            .isNotEqualTo(code)
            .hasSize(64)                                  // HMAC-SHA256 hex
            .isEqualTo(hasher.hash(code));
        // DTO 어디에도 평문이 없다
        assertThat(saved.getOtpId()).doesNotContain(code);
    }

    @Test
    @DisplayName("코드는 설정된 자릿수의 숫자다 — 앞자리 0 도 유지한다")
    void codeShapeFollowsPolicy() {
        for (int i = 0; i < 50; i++) {
            String code = service.issue(MEMBER_ID, null, PURPOSE, null);
            assertThat(code).hasSize(props.getLength()).containsOnlyDigits();
        }
    }

    @Test
    @DisplayName("이전 미소비 코드는 발급 시 폐기한다 — 유효한 코드가 둘이면 시도 기회가 배가 된다")
    void previousCodesAreDiscardedOnIssue() {
        service.issue(MEMBER_ID, null, PURPOSE, null);
        verify(mapper).deleteActiveByMember(MEMBER_ID, PURPOSE.name());
    }

    @Test
    @DisplayName("재발송 쿨다운 안에는 발급하지 않는다 — 피해자 메일함 폭탄 차단")
    void resendCooldownBlocksIssue() {
        when(mapper.findLastIssuedAt(MEMBER_ID, PURPOSE.name()))
            .thenReturn(LocalDateTime.now().minusSeconds(10));   // 쿨다운 60초

        assertThatThrownBy(() -> service.issue(MEMBER_ID, null, PURPOSE, null))
            .isInstanceOf(OtpThrottledException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("시간당 발급 상한을 넘으면 발급하지 않는다 — 쿨다운을 지키는 저속 남용 차단")
    void hourlyCapBlocksIssue() {
        when(mapper.countIssuedSince(eq(MEMBER_ID), eq(PURPOSE.name()), any()))
            .thenReturn(props.getMaxPerHour());

        assertThatThrownBy(() -> service.issue(MEMBER_ID, null, PURPOSE, null))
            .isInstanceOf(OtpThrottledException.class);
        verify(mapper, never()).insert(any());
    }

    // ── 검증 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 코드는 통과하고 즉시 소비된다")
    void validCodePassesAndIsConsumed() {
        MemberOtp otp = active("123456", 0);
        when(mapper.findLatestActive(MEMBER_ID, PURPOSE.name())).thenReturn(otp);
        when(mapper.markVerified(eq(otp.getOtpId()), any())).thenReturn(1);

        assertThat(service.verifyAndConsume(MEMBER_ID, PURPOSE, "123456")).isTrue();
        verify(mapper).markVerified(eq(otp.getOtpId()), any());
    }

    @Test
    @DisplayName("사용 완료 코드는 재사용되지 않는다 — markVerified 가 0을 반환하면 거부")
    void consumedCodeCannotBeReused() {
        MemberOtp otp = active("123456", 0);
        when(mapper.findLatestActive(MEMBER_ID, PURPOSE.name())).thenReturn(otp);
        // 동시 요청이 먼저 소비한 상황 = 갱신 행 0
        when(mapper.markVerified(eq(otp.getOtpId()), any())).thenReturn(0);

        assertThat(service.verifyAndConsume(MEMBER_ID, PURPOSE, "123456")).isFalse();
    }

    @Test
    @DisplayName("만료 코드는 거부하고 폐기한다")
    void expiredCodeIsRejectedAndDeleted() {
        MemberOtp otp = active("123456", 0);
        otp.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(mapper.findLatestActive(MEMBER_ID, PURPOSE.name())).thenReturn(otp);

        assertThat(service.verifyAndConsume(MEMBER_ID, PURPOSE, "123456")).isFalse();
        verify(mapper).deleteById(otp.getOtpId());
        verify(mapper, never()).markVerified(anyString(), any());
    }

    @Test
    @DisplayName("틀린 코드는 시도 횟수를 올린다 — 비교 전에 올려야 롤백으로 유실되지 않는다")
    void wrongCodeIncrementsAttempt() {
        MemberOtp otp = active("123456", 0);
        when(mapper.findLatestActive(MEMBER_ID, PURPOSE.name())).thenReturn(otp);

        assertThat(service.verifyAndConsume(MEMBER_ID, PURPOSE, "999999")).isFalse();
        verify(mapper).incrementAttempt(otp.getOtpId());
        verify(mapper, never()).markVerified(anyString(), any());
    }

    @Test
    @DisplayName("시도 상한에 닿으면 코드를 폐기한다 — 다음 요청을 기다리지 않는다")
    void maxAttemptsDiscardsCode() {
        MemberOtp otp = active("123456", props.getMaxAttempts() - 1);   // 이번이 마지막 시도
        when(mapper.findLatestActive(MEMBER_ID, PURPOSE.name())).thenReturn(otp);

        assertThat(service.verifyAndConsume(MEMBER_ID, PURPOSE, "999999")).isFalse();
        verify(mapper).deleteById(otp.getOtpId());
    }

    @Test
    @DisplayName("이미 상한을 넘긴 코드는 비교조차 하지 않고 폐기한다")
    void overLimitCodeIsDiscardedWithoutCompare() {
        MemberOtp otp = active("123456", props.getMaxAttempts());
        when(mapper.findLatestActive(MEMBER_ID, PURPOSE.name())).thenReturn(otp);

        assertThat(service.verifyAndConsume(MEMBER_ID, PURPOSE, "123456")).isFalse();
        verify(mapper).deleteById(otp.getOtpId());
        verify(mapper, never()).incrementAttempt(anyString());
    }

    @Test
    @DisplayName("유효한 코드가 없으면 조용히 실패한다 — 사유를 노출하지 않는다")
    void noActiveCodeFails() {
        when(mapper.findLatestActive(MEMBER_ID, PURPOSE.name())).thenReturn(null);
        assertThat(service.verifyAndConsume(MEMBER_ID, PURPOSE, "123456")).isFalse();
    }

    @Test
    @DisplayName("용도가 다르면 통과하지 않는다 — 교차 사용 방지")
    void purposeIsPartOfLookup() {
        when(mapper.findLatestActive(MEMBER_ID, OtpPurpose.EMAIL_VERIFY.name())).thenReturn(null);

        assertThat(service.verifyAndConsume(MEMBER_ID, OtpPurpose.EMAIL_VERIFY, "123456")).isFalse();
        verify(mapper).findLatestActive(MEMBER_ID, OtpPurpose.EMAIL_VERIFY.name());
    }

    // ------------------------------------------------------------------

    private MemberOtp active(String code, int attempts) {
        MemberOtp otp = new MemberOtp();
        otp.setOtpId("MOT_test");
        otp.setMemberId(MEMBER_ID);
        otp.setPurpose(PURPOSE.name());
        otp.setCodeHash(hasher.hash(code));
        otp.setExpiresAt(LocalDateTime.now().plus(Duration.ofMinutes(5)));
        otp.setAttemptCount(attempts);
        return otp;
    }
}
