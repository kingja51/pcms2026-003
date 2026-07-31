package com.gonet.primary.member.dormant.service;

import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.mail.MailService;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.dormant.mapper.DormantMapper;
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.otp.dto.OtpPurpose;
import com.gonet.primary.member.otp.service.MemberOtpService;
import com.gonet.primary.system.mail.dto.MailTemplate;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 휴면 해제 본인확인 구현 — 실명인증 / 이메일 OTP.
 *
 * <h2>계정 열거 차단</h2>
 * 이 화면은 <b>로그인 전에 누구나</b> 접근한다. 응답이 갈리면 그게 곧
 * "이 아이디는 휴면 계정으로 존재한다" 는 신호다. 두 가지를 함께 맞춘다:
 * <ol>
 *   <li><b>응답 내용</b> — {@link #requestOtp} 는 어떤 실패에도 예외를 던지지 않는다.
 *       계정 없음·이메일 불일치·쿨다운·메일 발송 실패가 전부 "정상 종료" 다</li>
 *   <li><b>응답 시간</b> — 존재하는 계정만 해시 계산·DB 조회·메일 발송을 하므로
 *       그냥 두면 <b>느린 응답 = 계정 있음</b>이 된다. {@value #MIN_RESPONSE_MILLIS}ms
 *       하한을 둬서 빠른 경로를 느린 경로에 맞춘다</li>
 * </ol>
 *
 * <p>시간 하한이 완벽한 방어는 아니다(메일 서버가 아주 느리면 하한을 넘긴다).
 * 그래서 레이트리밋(Bucket4j)이 함께 걸린다 — 통계적 차이를 읽으려면 많은 시도가
 * 필요한데, 그 전에 막힌다.
 *
 * <p><b>스레드를 재우는 것이 아깝지 않은가</b>: 운영은 가상 스레드라 블로킹 비용이
 * 캐리어 스레드를 잡지 않는다. 게다가 휴면 해제는 호출 빈도가 매우 낮은 경로다.
 */
@Service("dormantRestoreService")
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class DormantRestoreServiceImpl extends EgovAbstractServiceImpl implements DormantRestoreService {

    private static final Logger log = LoggerFactory.getLogger(DormantRestoreServiceImpl.class);

    /** 응답 시간 하한(ms) — 계정 유무에 따른 소요시간 차이를 덮는다. */
    private static final long MIN_RESPONSE_MILLIS = 400L;

    /** 확인 실패 시 사용자에게 보이는 <b>유일한</b> 메시지. 사유를 구분하지 않는다. */
    private static final String VERIFY_FAIL_MESSAGE = "본인 확인에 실패했습니다. 입력 내용을 다시 확인해 주세요.";

    private final DormantMapper    mapper;
    private final DormantService   dormantService;
    private final MemberOtpService otpService;
    private final EmailHasher      emailHasher;
    private final MailService      mailService;

    public DormantRestoreServiceImpl(DormantMapper mapper,
                                     DormantService dormantService,
                                     MemberOtpService otpService,
                                     EmailHasher emailHasher,
                                     MailService mailService) {
        this.mapper         = mapper;
        this.dormantService = dormantService;
        this.otpService     = otpService;
        this.emailHasher    = emailHasher;
        this.mailService    = mailService;
    }

    // ==================================================================
    // 수단 B — 이메일 OTP
    // ==================================================================

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void requestOtp(String loginId, String email, String clientIp) {
        long started = System.nanoTime();
        try {
            Member d = findDormantByEmail(loginId, email);
            if (d == null) {
                // 계정이 없거나 이메일이 다르다. 조용히 끝낸다 — 화면은 성공과 동일하다.
                log.info("DORMANT_OTP_REQUEST_NO_TARGET loginId={}", safe(loginId));
                return;
            }

            String code;
            try {
                code = otpService.issue(d.getMemberId(), d.getSiteId(), OtpPurpose.DORMANT_RESTORE, clientIp);
            } catch (Exception ex) {
                // 쿨다운·상한도 삼킨다. "잠시 후 다시 시도하세요" 를 보여 주면
                // 그 자체가 "계정이 존재한다" 는 뜻이 된다.
                log.info("DORMANT_OTP_THROTTLED memberId={} reason={}", d.getMemberId(), ex.getMessage());
                return;
            }

            // 수신처는 **휴면 스냅샷의 이메일**이다. 사용자가 입력한 주소로 보내지 않는다 —
            // 입력값으로 보내면 해시 일치 검사를 통과한 뒤에도 대소문자·별칭 차이로
            // 엉뚱한 곳에 갈 수 있다.
            try {
                Map<String, Object> model = new HashMap<>();
                model.put("memberName", d.getMemberName());
                model.put("loginId",    d.getLoginId());
                model.put("otpCode",    code);
                mailService.sendFromTemplate(MailTemplate.CODE_ACCOUNT_DORMANT_OTP, d.getEmail(), model);
            } catch (Exception ex) {
                // 메일 실패도 사용자에게 알리지 않는다(열거 통로). 운영자는 로그로 본다.
                log.warn("DORMANT_OTP_MAIL_FAIL memberId={} err={}", d.getMemberId(), ex.getMessage());
            }
        } finally {
            padResponseTime(started);
        }
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public Member restoreByOtp(String loginId, String email, String code) {
        long started = System.nanoTime();
        try {
            Member d = findDormantByEmail(loginId, email);
            if (d == null) {
                log.info("DORMANT_OTP_VERIFY_NO_TARGET loginId={}", safe(loginId));
                throw new IllegalArgumentException(VERIFY_FAIL_MESSAGE);
            }
            if (!otpService.verifyAndConsume(d.getMemberId(), OtpPurpose.DORMANT_RESTORE, code)) {
                throw new IllegalArgumentException(VERIFY_FAIL_MESSAGE);
            }
            return restoreOrFail(d.getMemberId(), "OTP");
        } finally {
            padResponseTime(started);
        }
    }

    // ==================================================================
    // 수단 A — 실명인증(DI 해시 대조)
    // ==================================================================

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public Member restoreByIdentity(String loginId, String diHash) {
        long started = System.nanoTime();
        try {
            String normLogin = normalize(loginId);
            if (normLogin.isEmpty() || diHash == null || diHash.isBlank()) {
                throw new IllegalArgumentException(VERIFY_FAIL_MESSAGE);
            }
            Member d = mapper.findDormantByLoginIdAndDiHash(normLogin, diHash);
            if (d == null) {
                // 아이디가 없는 것인지 DI 가 다른 것인지 구분하지 않는다.
                log.info("DORMANT_IDENTITY_NO_MATCH loginId={}", safe(loginId));
                throw new IllegalArgumentException(VERIFY_FAIL_MESSAGE);
            }
            return restoreOrFail(d.getMemberId(), "IDENTITY");
        } finally {
            padResponseTime(started);
        }
    }

    // ==================================================================

    /** 로그인ID + 이메일 해시로 휴면 스냅샷 조회. 어느 하나라도 비면 {@code null}. */
    private Member findDormantByEmail(String loginId, String email) {
        String normLogin = normalize(loginId);
        if (normLogin.isEmpty() || email == null || email.isBlank()) return null;
        String emailHash = emailHasher.hash(email);
        if (emailHash == null || emailHash.isEmpty()) return null;
        return mapper.findDormantByLoginIdAndEmailHash(normLogin, emailHash);
    }

    /** 역이관 실행. 확인은 이미 끝났으므로 여기서 실패하면 시스템 문제다. */
    private Member restoreOrFail(String memberId, String via) {
        Member restored = dormantService.restoreVerified(memberId);
        if (restored == null) {
            // 확인은 통과했는데 대상이 사라졌다 — 동시에 배치가 파기했거나 데이터 불일치.
            log.error("DORMANT_RESTORE_VANISHED memberId={} via={}", memberId, via);
            throw new IllegalStateException("휴면 해제 처리 중 문제가 발생했습니다.");
        }
        log.info("DORMANT_RESTORED memberId={} via={}", memberId, via);
        return restored;
    }

    private static String normalize(String loginId) {
        return loginId == null ? "" : loginId.trim().toLowerCase();
    }

    /** 로그에 남길 로그인ID — 전체를 남기면 로그 자체가 열거 자료가 된다. */
    private static String safe(String loginId) {
        if (loginId == null || loginId.isBlank()) return "(empty)";
        String t = loginId.trim();
        return t.length() <= 2 ? t.charAt(0) + "*" : t.substring(0, 2) + "***";
    }

    /**
     * 시작 시각부터 {@value #MIN_RESPONSE_MILLIS}ms 가 지나도록 대기한다.
     * 이미 넘겼으면 즉시 반환한다.
     */
    private static void padResponseTime(long startedNanos) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        long remain = MIN_RESPONSE_MILLIS - elapsedMs;
        if (remain <= 0) return;
        try {
            Thread.sleep(remain);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();   // 인터럽트 상태를 삼키지 않는다
        }
    }
}
