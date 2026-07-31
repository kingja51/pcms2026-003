package com.gonet.primary.member.otp.service;

import com.gonet.common.crypto.TokenHasher;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.otp.config.OtpProperties;
import com.gonet.primary.member.otp.dto.MemberOtp;
import com.gonet.primary.member.otp.dto.OtpPurpose;
import com.gonet.primary.member.otp.mapper.MemberOtpMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * OTP 발급·검증 구현.
 *
 * <h2>이 클래스가 지키는 것</h2>
 * <ol>
 *   <li><b>평문 미보관</b> — DB 에는 HMAC-SHA256 만({@link TokenHasher}). AES 마스터키가 아니라 PII HMAC 키를
 *       쓴다({@code PCMS_PII_HMAC_KEY}) — 암호화 키와 분리돼 있어야 한 쪽이 털려도
 *       다른 쪽이 버틴다(D11)</li>
 *   <li><b>상수 시간 비교</b> — {@link TokenHasher#matches}. {@code String.equals} 는
 *       첫 불일치에서 빠져나와 <b>일치한 접두 길이가 응답 시간에 새어 나온다</b></li>
 *   <li><b>1회용</b> — 소비는 {@code WHERE verified_at IS NULL} UPDATE 의 반환 행 수로
 *       판정한다. 자바 if 로는 동시 요청 둘이 함께 통과할 수 있다</li>
 *   <li><b>시도 상한</b> — 카운터는 세션이 아니라 행에 있다. 쿠키를 버려도 초기화되지 않는다</li>
 *   <li><b>발급 제한</b> — 쿨다운 + 시간당 상한. 피해자 메일함 폭탄을 막는다</li>
 * </ol>
 *
 * <p>난수는 {@link SecureRandom} 이다. {@code Math.random()} 은 예측 가능해
 * 인증 코드에 쓰면 안 된다.
 */
@Service("memberOtpService")
@EnableConfigurationProperties(OtpProperties.class)   // 소비자가 등록하는 규약 — AesGcmCipher/FlywayConfig 와 동일
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberOtpServiceImpl extends EgovAbstractServiceImpl implements MemberOtpService {

    private static final Logger log = LoggerFactory.getLogger(MemberOtpServiceImpl.class);

    private final MemberOtpMapper mapper;
    private final OtpProperties   props;
    private final TokenHasher     hasher;
    private final SecureRandom    random = new SecureRandom();

    public MemberOtpServiceImpl(MemberOtpMapper mapper, OtpProperties props, TokenHasher hasher) {
        this.mapper = mapper;
        this.props  = props;
        this.hasher = hasher;
    }

    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String issue(String memberId, String siteId, OtpPurpose purpose, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        String purposeName = purpose.name();

        // ── 발급 제한 ─────────────────────────────────────────────────────
        LocalDateTime last = mapper.findLastIssuedAt(memberId, purposeName);
        if (last != null && last.plus(props.getResendCooldown()).isAfter(now)) {
            throw new OtpThrottledException("재발송 쿨다운 미경과");
        }
        int recent = mapper.countIssuedSince(memberId, purposeName, now.minusHours(1));
        if (recent >= props.getMaxPerHour()) {
            throw new OtpThrottledException("시간당 발급 상한 초과");
        }

        // 이전 미소비 코드 폐기 — 동시에 여러 코드가 유효하면 시도 기회가 배로 늘어난다
        mapper.deleteActiveByMember(memberId, purposeName);

        String code = randomDigits(props.getLength());

        MemberOtp otp = new MemberOtp();
        otp.setOtpId("MOT_" + UuidV7Generator.generate());
        otp.setMemberId(memberId);
        otp.setSiteId(siteId);
        otp.setPurpose(purposeName);
        otp.setCodeHash(hasher.hash(code));
        otp.setExpiresAt(now.plus(props.getTtl()));
        otp.setClientIp(clientIp);
        otp.setCreatedBy("SYSTEM");
        otp.setCreatedIp(clientIp);
        mapper.insert(otp);

        // ⚠️ code 는 절대 로그에 남기지 않는다.
        log.info("OTP_ISSUED memberId={} purpose={} expiresAt={}", memberId, purposeName, otp.getExpiresAt());
        return code;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public boolean verifyAndConsume(String memberId, OtpPurpose purpose, String code) {
        LocalDateTime now = LocalDateTime.now();
        String purposeName = purpose.name();

        MemberOtp otp = mapper.findLatestActive(memberId, purposeName);
        if (otp == null) {
            log.info("OTP_VERIFY_FAIL memberId={} purpose={} reason=NO_ACTIVE_CODE", memberId, purposeName);
            return false;
        }
        if (otp.isExpired(now)) {
            mapper.deleteById(otp.getOtpId());   // 만료 코드는 남겨 둘 이유가 없다
            log.info("OTP_VERIFY_FAIL memberId={} purpose={} reason=EXPIRED", memberId, purposeName);
            return false;
        }
        if (otp.getAttemptCount() >= props.getMaxAttempts()) {
            mapper.deleteById(otp.getOtpId());
            log.warn("OTP_DISCARDED memberId={} purpose={} reason=MAX_ATTEMPTS", memberId, purposeName);
            return false;
        }

        // 시도 횟수는 비교 **전에** 올린다. 뒤에 올리면 예외·롤백으로 카운트가 유실돼
        // 무제한 대입이 가능해진다.
        mapper.incrementAttempt(otp.getOtpId());

        boolean match = hasher.matches(otp.getCodeHash(), code);
        if (!match) {
            // 이번 시도로 상한에 닿았으면 즉시 폐기 — 다음 요청을 기다리지 않는다
            if (otp.getAttemptCount() + 1 >= props.getMaxAttempts()) {
                mapper.deleteById(otp.getOtpId());
                log.warn("OTP_DISCARDED memberId={} purpose={} reason=MAX_ATTEMPTS_REACHED", memberId, purposeName);
            }
            log.info("OTP_VERIFY_FAIL memberId={} purpose={} reason=MISMATCH", memberId, purposeName);
            return false;
        }

        // 소비는 DB 가 판정한다 — 동시 요청 둘 중 하나만 1을 받는다
        int consumed = mapper.markVerified(otp.getOtpId(), now);
        if (consumed == 0) {
            log.warn("OTP_REPLAY_BLOCKED memberId={} purpose={}", memberId, purposeName);
            return false;
        }
        log.info("OTP_VERIFIED memberId={} purpose={}", memberId, purposeName);
        return true;
    }

    // ------------------------------------------------------------------

    /** 앞자리 0 을 포함한 고정 길이 숫자 코드. 자리마다 균등 추출한다. */
    private String randomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append((char) ('0' + random.nextInt(10)));
        return sb.toString();
    }

}
