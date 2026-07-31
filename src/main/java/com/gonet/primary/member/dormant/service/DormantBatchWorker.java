package com.gonet.primary.member.dormant.service;

import com.gonet.common.mail.MailService;
import com.gonet.primary.member.dormant.dto.DormantCandidate;
import com.gonet.primary.member.dormant.mapper.DormantMapper;
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.mapper.MemberMapper;
import com.gonet.primary.system.mail.dto.MailTemplate;
import com.gonet.primary.system.pii.dto.PiiPurgeReason;
import com.gonet.primary.system.pii.dto.PiiPurgeUserType;
import com.gonet.primary.system.pii.service.PiiPurgeLogger;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Dormant 배치 단건 처리기 — {@link DormantServiceImpl} 루프에서 호출.
 *
 * <p>별도 {@link Component} 로 분리된 이유: {@code @Transactional(REQUIRES_NEW)} 는
 * Spring AOP 프록시 경계에서만 적용된다. 서비스 내부 self-invocation 시 프록시를 우회해
 * 트랜잭션이 새로 시작되지 않으므로, 단건 격리가 필요한 메서드는 항상 **다른 빈**에서 호출해야 한다.
 *
 * <p>각 process* 는 {@code REQUIRES_NEW} — 한 건의 실패가 다른 건에 영향을 주지 않는다.
 */
@Component
public class DormantBatchWorker {

    private static final Logger log = LoggerFactory.getLogger(DormantBatchWorker.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DormantMapper   mapper;
    private final MemberMapper    memberMapper;
    private final MailService     mailService;
    private final PiiPurgeLogger  piiPurgeLogger;

    public DormantBatchWorker(DormantMapper mapper,
                               MemberMapper memberMapper,
                               MailService mailService,
                               PiiPurgeLogger piiPurgeLogger) {
        this.mapper = mapper;
        this.memberMapper = memberMapper;
        this.mailService = mailService;
        this.piiPurgeLogger = piiPurgeLogger;
    }

    /** 단계별 알림 — 이력 INSERT + 메일 1건 발송. 둘 다 성공해야 커밋. */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void processNotice(DormantCandidate c, String stage,
                               String templateCode, int daysLeft) throws MessagingException {
        mapper.insertNotice(c.getMemberId(), stage);
        Map<String, Object> model = new HashMap<>();
        model.put("memberName",  c.getMemberName());
        model.put("loginId",     c.getLoginId());
        model.put("lastLoginAt", c.getLastLoginAt());
        model.put("daysLeft",    daysLeft);
        mailService.sendFromTemplate(templateCode, c.getEmail(), model);
    }

    /**
     * 전환 — tb_member → tb_member_dormant 이관. 이관은 트랜잭션 내 원자적.
     * 이관 후 메일 발송은 별도 try (실패해도 이관은 유지 — 이미 1년 경과).
     *
     * @param retentionYears 보관 기한(년) — 메일 표시용
     */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void processTransfer(DormantCandidate c, int retentionYears) {
        int inserted = mapper.insertDormantFromMember(c.getMemberId(), "INACTIVE_1Y");
        if (inserted == 0) {
            throw new IllegalStateException("failed to insert into tb_member_dormant: " + c.getMemberId());
        }
        int deleted = mapper.deleteMember(c.getMemberId());
        if (deleted == 0) {
            throw new IllegalStateException("failed to delete from tb_member: " + c.getMemberId());
        }
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("memberName",     c.getMemberName());
            model.put("loginId",        c.getLoginId());
            model.put("dormantAt",      LocalDate.now().format(DATE));
            model.put("retentionUntil", LocalDate.now().plusYears(retentionYears).format(DATE));
            mailService.sendFromTemplate(
                MailTemplate.CODE_ACCOUNT_DORMANT_TRANSFERRED, c.getEmail(), model);
        } catch (Exception ex) {
            log.warn("DORMANT_TRANSFERRED_MAIL_FAIL memberId={} err={}",
                c.getMemberId(), ex.getMessage());
        }
    }

    /**
     * 휴면 1년 만기 → tb_member_withdraw 로 전환.
     *
     * <p>흐름 (단일 트랜잭션):
     * <ol>
     *   <li>dormant row fetch — PII 포함 Member 객체</li>
     *   <li>memberMapper.insertWithdraw — 법정 최소 항목만(login_id_hash, di_hash, withdraw_at, retention_expire_at)
     *       으로 tb_member_withdraw 에 INSERT. 평문 PII 는 모두 버림</li>
     *   <li>dormantMapper.hardDeleteDormant — tb_member_dormant row 제거</li>
     *   <li>piiPurgeLogger.write — PII 제거 감사 (별도 트랜잭션 — REQUIRES_NEW + accessLogExecutor)</li>
     * </ol>
     *
     * <p>{@code REQUIRES_NEW} 로 단건 격리. 1건 실패해도 다음 회원 진행. INSERT/DELETE 가 같은
     * 트랜잭션이라 부분 실패 (withdraw INSERT 됐지만 dormant DELETE 실패) 자동 롤백.
     *
     * <p>실제 영구 삭제는 {@link com.gonet.scheduler.WithdrawPurgeScheduler} 가
     * {@code retention_expire_at &lt;= now} 일 때 hard delete.
     *
     * @param retentionYearsAfterTransfer 탈퇴 후 보존 기간(년) — withdraw_at + N년 = retention_expire_at
     */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void processTransferToWithdraw(String memberId, int retentionYearsAfterTransfer) {
        Member dormant = mapper.findDormantById(memberId);
        if (dormant == null) {
            log.info("===DORMANT_TRANSFER_SKIP memberId={} reason=already-transferred-or-not-found", memberId);
            return;
        }
        LocalDateTime retentionExpireAt = LocalDateTime.now().plusYears(retentionYearsAfterTransfer);

        // 법정 최소 항목으로 withdraw INSERT — login_id_hash 는 SQL 측 SHA2, di_hash 는 dormant 의 값 그대로.
        // status=DORMANT_EXPIRED 카테고리, reason=자동 발생 안내 (운영자 식별용)
        memberMapper.insertWithdraw(
            dormant,
            "DORMANT_EXPIRED",
            "휴면 1년 만기 자동 전환",
            retentionExpireAt);

        int deleted = mapper.hardDeleteDormant(memberId);
        if (deleted == 0) {
            // 동시성으로 다른 트랜잭션이 이미 처리한 경우 — withdraw INSERT 만 있고 dormant 는 사라진 상태
            // 같은 트랜잭션이라 throw 하면 withdraw INSERT 도 롤백 — 안전
            throw new IllegalStateException("dormant row disappeared mid-transfer: " + memberId);
        }

        // 감사 적재 — 별도 트랜잭션 (호출자 rollback 영향 없음). reason 은 기존 코드 호환.
        piiPurgeLogger.write(
            PiiPurgeUserType.MEMBER,
            memberId,
            PiiPurgeReason.MEMBER_DORMANT_EXPIRY,
            "tb_member_dormant");
    }
}
