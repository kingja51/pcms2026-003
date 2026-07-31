package com.gonet.primary.system.pii.service;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.system.pii.dto.PiiPurgeLog;
import com.gonet.primary.system.pii.dto.PiiPurgeReason;
import com.gonet.primary.system.pii.dto.PiiPurgeUserType;
import com.gonet.primary.system.pii.mapper.PiiPurgeLogMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * {@link PiiPurgeLogger} 구현 — tb_pii_purge_log INSERT.
 *
 * <p>설계 (§0.46 M3 + §0.48 회귀 수정 가르침):
 * <ul>
 *   <li>{@code @Async("accessLogExecutor")} — 응답 commit 직후 백그라운드 INSERT</li>
 *   <li>{@code @Transactional(REQUIRES_NEW, primaryTransactionManager)} — 호출자(파기 배치) 와 분리</li>
 *   <li>{@code @Lazy PiiPurgeLogger self} 자기 프록시 주입 — self-invocation 함정 회피</li>
 *   <li>fallback logger {@code com.gonet.security.fallback} — DB 적재 실패 시 ERROR 라인</li>
 * </ul>
 *
 * <p>{@code user_id_hash} 는 SHA-256(userPk) hex — 역추적 불가, 추적 가능.
 */
@Service
public class PiiPurgeLoggerImpl extends EgovAbstractServiceImpl implements PiiPurgeLogger {

    private static final Logger log      = LoggerFactory.getLogger(PiiPurgeLoggerImpl.class);
    /** SIEM 직접 수집용 fallback 로거 — RollingFileAppender 분리 권장. */
    private static final Logger fallback = LoggerFactory.getLogger("com.gonet.security.fallback");

    private final PiiPurgeLogMapper mapper;
    /** §0.48 회귀 수정 — self-invocation 회피. {@code @Lazy} 로 순환 주입 회피. */
    private final PiiPurgeLogger    self;

    @Autowired
    public PiiPurgeLoggerImpl(PiiPurgeLogMapper mapper, @Lazy PiiPurgeLogger self) {
        this.mapper = mapper;
        this.self   = self;
    }

    /** 단위 테스트용 보조 생성자 — Spring 프록시 없이 직접 인스턴스화. */
    public PiiPurgeLoggerImpl(PiiPurgeLogMapper mapper) {
        this(mapper, null);
    }

    @Override
    public void write(PiiPurgeUserType userType, String userPk,
                       PiiPurgeReason reason, String tableList) {
        if (userType == null || userPk == null || reason == null) {
            log.warn("PII_PURGE_LOG_SKIP invalid args userType={} userPk={} reason={}",
                userType, mask(userPk), reason);
            return;
        }
        PiiPurgeLog row = newRow(userType, userPk, reason.name(), reason.legalBasis(), tableList);
        dispatch(row);
    }

    @Override
    public void writeWithFreeReason(PiiPurgeUserType userType, String userPk,
                                     String freeReason, String legalBasis, String tableList) {
        if (userType == null || userPk == null || freeReason == null || freeReason.isBlank()) {
            log.warn("PII_PURGE_LOG_SKIP free invalid args userType={} userPk={}",
                userType, mask(userPk));
            return;
        }
        PiiPurgeLog row = newRow(userType, userPk, freeReason, legalBasis, tableList);
        dispatch(row);
    }

    /** self != null (Spring 프록시) 면 프록시 호출 → @Async + @Transactional 적용. null 이면 단위 테스트 — 직접 동기 호출. */
    private void dispatch(PiiPurgeLog row) {
        if (self != null) self.writeAsync(row);
        else              writeAsync(row);
    }

    @Override
    @Async("accessLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW,
                   transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void writeAsync(PiiPurgeLog row) {
        try {
            mapper.insert(row);
            log.info("===PII_PURGE_LOG_OK userType={} reason={} tables={}",
                row.getUserType(), row.getPurgeReason(), row.getTableList());
        } catch (Exception ex) {
            // 컴플라이언스 — DB 적재 실패해도 SIEM/syslog 로 흔적 남김
            fallback.error("PII_PURGE_LOG_FAIL userType={} userIdHash={} reason={} tables={} legal={} reason_err={}",
                row.getUserType(), row.getUserIdHash(), row.getPurgeReason(),
                row.getTableList(), row.getLegalBasis(), ex.getMessage());
            log.warn("PII_PURGE_LOG_INSERT_FAIL reason={} reason_err={}",
                row.getPurgeReason(), ex.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private PiiPurgeLog newRow(PiiPurgeUserType userType, String userPk,
                                String reason, String legalBasis, String tableList) {
        PiiPurgeLog row = new PiiPurgeLog();
        row.setPiiPurgeLogId(UuidV7Generator.generate());
        row.setUserType(userType.name());
        row.setUserIdHash(sha256Hex(userPk));
        LocalDateTime now = LocalDateTime.now();
        row.setPurgedAt(now);
        row.setPurgeReason(trim(reason, 100));
        row.setTableList(trim(tableList, 500));
        row.setLegalBasis(trim(legalBasis, 500));
        // 6감사컬럼 — 호출자 컨텍스트가 비어있을 수 있으므로 SYSTEM 폴백
        String actor = AuditContext.userId();
        String ip    = AuditContext.ip();
        row.setCreatedBy(actor);
        row.setCreatedIp(ip);
        row.setCreatedAt(now);
        row.setUpdatedBy(actor);
        row.setUpdatedIp(ip);
        row.setUpdatedAt(now);
        return row;
    }

    private static String sha256Hex(String s) {
        if (s == null) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 은 표준 JDK 보장 — 도달 불가
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /** 로그 출력용 마스킹 — userPk 의 앞 6자만 노출. */
    private static String mask(String s) {
        if (s == null) return "null";
        return s.length() <= 6 ? s : (s.substring(0, 6) + "***");
    }

    private static String trim(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
