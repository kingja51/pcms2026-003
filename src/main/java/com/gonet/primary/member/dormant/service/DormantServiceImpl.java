package com.gonet.primary.member.dormant.service;

import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.mail.MailService;
import com.gonet.primary.member.dormant.dto.DormantCandidate;
import com.gonet.primary.member.dormant.mapper.DormantMapper;
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.system.mail.dto.MailTemplate;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 회원 휴면 라이프사이클 구현.
 *
 * <p>cutoff 일수:
 * <ul>
 *   <li>30D 알림 = 335일 (전환까지 30일 남음)</li>
 *   <li>7D 알림  = 358일 (전환까지 7일 남음)</li>
 *   <li>1D 알림  = 364일 (전환까지 1일 남음)</li>
 *   <li>전환     = 365일 이상</li>
 * </ul>
 *
 * <p>각 단건은 {@link DormantBatchWorker} 가 독립 트랜잭션({@code REQUIRES_NEW})으로 처리 —
 * 1건 실패해도 다음 회원 진행. self-invocation 으로 인한 AOP 우회를 막기 위해 별도 빈으로 분리.
 */
@Service
@Transactional(readOnly = true, transactionManager =
    com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
public class DormantServiceImpl extends EgovAbstractServiceImpl implements DormantService {

    private static final Logger log = LoggerFactory.getLogger(DormantServiceImpl.class);

    /** 전환 기준 일수. */
    public static final int DAYS_UNTIL_DORMANT = 365;
    public static final int CUTOFF_30D = DAYS_UNTIL_DORMANT - 30;   // 335
    public static final int CUTOFF_7D  = DAYS_UNTIL_DORMANT - 7;    // 358
    public static final int CUTOFF_1D  = DAYS_UNTIL_DORMANT - 1;    // 364
    /**
     * 휴면 보관 기한(년) — 휴면 → 탈퇴(tb_member_withdraw) 전환 cutoff.
     * 사용자 안내 문구용으로도 사용 (메일 템플릿 retentionUntil).
     * 1년: 휴면 후 1년이 지나면 탈퇴 회원으로 전환 + 평문 PII 제거.
     * (영구 삭제는 추가로 {@code tb_member_withdraw.retention_expire_at} 만료 후 — WithdrawPurgeScheduler 처리)
     */
    public static final int RETENTION_YEARS = 1;

    /**
     * 탈퇴 후 보존 기간(년) — withdraw_at + N년 = retention_expire_at.
     * 회원 1년 (PIPA §29 기본). 만료 시 WithdrawPurgeScheduler 가 영구 삭제.
     */
    public static final int WITHDRAW_RETENTION_YEARS = 1;

    /** 1회 배치당 최대 전환 행 수 — 트랜잭션 폭증 방지. 다음 사이클이 다시 처리. */
    private static final int PURGE_BATCH_LIMIT = 500;

    private final DormantMapper      mapper;
    private final DormantBatchWorker worker;
    private final MailService        mailService;
    private final EmailHasher        emailHasher;
    private final PasswordEncoder    passwordEncoder;

    public DormantServiceImpl(DormantMapper mapper,
                               DormantBatchWorker worker,
                               MailService mailService,
                               EmailHasher emailHasher,
                               PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.worker = worker;
        this.mailService = mailService;
        this.emailHasher = emailHasher;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================================================================
    // 배치 진입점
    // ==================================================================

    @Override
    public void runDaily() {
        DormantBatchResult r = runDailyOnce();
        log.info("===DORMANT_BATCH summary notified30D={} notified7D={} notified1D={} transferred={} purged={} mailFailures={}",
            r.notified30D(), r.notified7D(), r.notified1D(), r.transferred(), r.purged(), r.mailFailures());
    }

    @Override
    public DormantBatchResult runDailyOnce() {
        int[] failures = { 0 };

        int n30 = notifyStage("30D", CUTOFF_30D, MailTemplate.CODE_ACCOUNT_DORMANT_NOTICE_30D, 30, failures);
        int n7  = notifyStage("7D",  CUTOFF_7D,  MailTemplate.CODE_ACCOUNT_DORMANT_NOTICE_7D,   7, failures);
        int n1  = notifyStage("1D",  CUTOFF_1D,  MailTemplate.CODE_ACCOUNT_DORMANT_NOTICE_1D,   1, failures);
        int tx  = transferExpired(failures);
        int pg  = purgeExpired(failures);

        return new DormantBatchResult(n30, n7, n1, tx, pg, failures[0]);
    }

    // ==================================================================
    // 단계별 알림
    // ==================================================================

    private int notifyStage(String stage, int cutoffDays, String templateCode,
                             int daysLeft, int[] failures) {
        List<DormantCandidate> candidates = mapper.findNoticeCandidates(cutoffDays, stage);
        int sent = 0;
        for (DormantCandidate c : candidates) {
            try {
                worker.processNotice(c, stage, templateCode, daysLeft);
                sent++;
            } catch (DuplicateKeyException dup) {
                log.info("===DORMANT_NOTICE_SKIP memberId={} stage={} reason=already-sent",
                    c.getMemberId(), stage);
            } catch (Exception ex) {
                failures[0]++;
                log.warn("DORMANT_NOTICE_FAIL memberId={} stage={} err={}",
                    c.getMemberId(), stage, ex.getMessage());
            }
        }
        log.info("===DORMANT_NOTICE stage={} candidates={} sent={}", stage, candidates.size(), sent);
        return sent;
    }

    // ==================================================================
    // 휴면 전환
    // ==================================================================

    /**
     * 휴면 1년 만기 row 를 tb_member_withdraw 로 전환 + tb_pii_purge_log 적재.
     * 평문 PII 제거(법정 최소 항목만 보존), 영구 삭제는 WithdrawPurgeScheduler 가 추가 1년 후.
     * 1회 호출 당 {@link #PURGE_BATCH_LIMIT} 건까지 — 폭증 방지.
     *
     * @return 전환 처리된 건수 (DormantBatchResult 의 'purged' 필드 호환 위해 이름 유지)
     */
    private int purgeExpired(int[] failures) {
        int retentionDays = RETENTION_YEARS * 365;
        List<String> ids = mapper.findExpiredDormantIds(retentionDays, PURGE_BATCH_LIMIT);
        int transferred = 0;
        for (String memberId : ids) {
            try {
                worker.processTransferToWithdraw(memberId, WITHDRAW_RETENTION_YEARS);
                transferred++;
            } catch (Exception ex) {
                failures[0]++;
                log.warn("DORMANT_TRANSFER_FAIL memberId={} err={}",
                    memberId, ex.getMessage(), ex);
            }
        }
        log.info("===DORMANT_TRANSFER candidates={} transferred={} cutoffDays={} retentionExpireYears={}",
            ids.size(), transferred, retentionDays, WITHDRAW_RETENTION_YEARS);
        return transferred;
    }

    private int transferExpired(int[] failures) {
        List<DormantCandidate> candidates = mapper.findTransferCandidates(DAYS_UNTIL_DORMANT);
        int transferred = 0;
        for (DormantCandidate c : candidates) {
            try {
                worker.processTransfer(c, RETENTION_YEARS);
                transferred++;
                log.info("===DORMANT_TRANSFER ok memberId={} loginId={}", c.getMemberId(), c.getLoginId());
            } catch (Exception ex) {
                failures[0]++;
                log.warn("DORMANT_TRANSFER_FAIL memberId={} err={}",
                    c.getMemberId(), ex.getMessage(), ex);
            }
        }
        log.info("===DORMANT_TRANSFER candidates={} transferred={}", candidates.size(), transferred);
        return transferred;
    }

    // ==================================================================
    // 복원
    // ==================================================================

    /**
     * 본인확인이 끝난 뒤의 역이관. 확인은 호출자가 마치고 들어온다 —
     * 인터페이스 javadoc 참조.
     *
     * <p>insert 와 delete 는 <b>같은 트랜잭션</b>이어야 한다. 하나만 성공하면
     * 회원이 두 테이블에 동시에 있거나 어디에도 없게 된다. 그래서 실패 시
     * {@link IllegalStateException} 으로 전체를 되돌린다.
     *
     * <p>복원 안내 메일 실패는 <b>롤백 사유가 아니다</b> — 해제는 이미 성공했고,
     * 메일 때문에 되돌리면 사용자는 아무것도 못 하게 된다. 경고만 남긴다.
     */
    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public Member restoreVerified(String memberId) {
        if (memberId == null || memberId.isBlank()) return null;

        Member d = mapper.findDormantById(memberId);
        if (d == null) {
            log.warn("DORMANT_RESTORE_NO_TARGET memberId={}", memberId);
            return null;
        }

        int inserted = mapper.insertMemberFromDormant(memberId);
        if (inserted == 0) {
            throw new IllegalStateException("failed to insert into tb_member on restore: " + memberId);
        }
        int deleted = mapper.deleteDormant(memberId);
        if (deleted == 0) {
            throw new IllegalStateException("failed to delete from tb_member_dormant on restore: " + memberId);
        }

        try {
            Map<String, Object> model = new HashMap<>();
            model.put("memberName", d.getMemberName());
            model.put("loginId",    d.getLoginId());
            mailService.sendFromTemplate(
                MailTemplate.CODE_ACCOUNT_DORMANT_RESTORED, d.getEmail(), model);
        } catch (Exception ex) {
            log.warn("DORMANT_RESTORED_MAIL_FAIL memberId={} err={}", memberId, ex.getMessage());
        }
        log.info("===DORMANT_RESTORE ok memberId={} loginId={}", memberId, d.getLoginId());
        return d;
    }
}
