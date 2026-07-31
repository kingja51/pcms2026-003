package com.gonet.primary.file.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.file.security.FileStorage;
import com.gonet.common.util.JsonUtils;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.file.config.FilePurgeProperties;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.mapper.FileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 파일 물리 삭제 배치 — {@link com.gonet.primary.file.retention.FileRetentionTarget} 경유로
 * {@code SoftDeleteRetentionScheduler} 가 호출한다(2026-08-01 트리거 통일).
 *
 * <p>처리 대상: {@code tb_file.delete_yn='Y'} + {@code updated_at < (now - retentionDays)}.
 * 유예 기간은 "문제 있는 파일을 숨기려고 삭제한 조작" 을 감지하기 위한 감사 창구.
 *
 * <p>각 파일 순서:
 * <ol>
 *   <li>uploads/{storedPath} 물리 삭제</li>
 *   <li>thumbnail/{thumbnailPath} 물리 삭제 (null 아닐 때만)</li>
 *   <li>quarantine/{storedPath} 정리 (정상 흐름엔 남아있지 않지만 안전차)</li>
 *   <li>tb_file 행 hard delete</li>
 *   <li>FILE_PURGE 감사 이벤트 기록 — 원본명/경로/hash/사유 보존</li>
 * </ol>
 *
 * <p>개별 파일 처리 중 예외 발생 시 로그만 남기고 다음 파일 계속 — 하나 실패가 전체 중단을 막는다.
 * 파일 삭제는 성공했으나 DB hard delete 가 실패한 경우는 다음 배치에서 자동 재시도
 * (파일이 이미 없으면 Files.deleteIfExists 는 no-op).
 */
@Service
public class FilePurgeService {

    private static final Logger log = LoggerFactory.getLogger(FilePurgeService.class);

    private final FileMapper           fileMapper;
    private final FileStorage          storage;
    private final FilePurgeProperties  properties;
    private final AuditLogger          auditLogger;

    public FilePurgeService(FileMapper fileMapper,
                            FileStorage storage,
                            FilePurgeProperties properties,
                            AuditLogger auditLogger) {
        this.fileMapper  = fileMapper;
        this.storage     = storage;
        this.properties  = properties;
        this.auditLogger = auditLogger;
    }

    /** 배치 1회 실행 — 스케줄러 또는 수동 호출 가능. 자체 cutoff 사용. */
    /**
     * <b>수동 트리거 전용</b> — 자동 스케줄에서는 호출되지 않는다(2026-08-01 통일).
     *
     * <p>cutoff 를 {@code gopcms.file.purge.retention-days} 로 자체 계산하므로
     * retention bucket 과 값이 다를 수 있다. 정기 정리는 {@code FileRetentionTarget}
     * 경로가 담당하고, 이 메서드는 운영자가 즉시 한 번 돌리고 싶을 때만 쓴다.
     * {@code gopcms.file.purge.enabled=false} 면 아무것도 하지 않는다.
     */
    public PurgeResult runOnce() {
        if (!properties.isEnabled()) {
            log.info("===FILE_PURGE skip (disabled by config)");
            return new PurgeResult(0, 0, 0);
        }

        LocalDateTime cutoff = LocalDateTime.now()
            .minusDays(properties.getRetentionDays());
        return runForCutoff(cutoff);
    }

    /**
     * 외부에서 결정한 cutoff 로 정리 — soft-delete retention 인프라가 호출.
     *
     * <p>{@link com.gonet.primary.file.retention.FileRetentionTarget} 가 본 메서드로 위임하면
     * RetentionScheduler 의 bucket cutoff 가 그대로 적용된다. 디스크 파일(upload/quarantine/thumbnail)
     * 까지 통합 정리되어 retention 이 DB row 만 삭제하고 디스크 orphan 을 만드는 회귀를 차단.
     *
     * <p><b>이 메서드가 실제 정리의 단일 진입점이다</b>(2026-08-01 트리거 통일).
     * {@code FileRetentionTarget} → {@code SoftDeleteRetentionScheduler} 경로로만 불린다.
     *
     * <p>{@code gopcms.file.purge.enabled} 는 보지 않는다 — 호출자(retention)의 의도를
     * 우선한다. 자동 실행을 멈추려면 {@code gopcms.retention.enabled=false} 를 쓴다.
     * 실제 삭제는 {@code gopcms.file.purge.dry-run} 이 아래에서 한 번 더 막는다.
     */
    public PurgeResult runForCutoff(LocalDateTime cutoff) {
        List<FileItem> candidates;
        try {
            candidates = fileMapper.findPurgeCandidates(cutoff, properties.getBatchSize());
        } catch (Exception ex) {
            log.error("FILE_PURGE candidate query failed: {}", ex.getMessage(), ex);
            return new PurgeResult(0, 0, 0);
        }

        if (candidates.isEmpty()) {
            // cutoff 는 호출자(retention bucket)가 결정한다. retentionDays 는 runOnce
            // 전용 값이라 여기 찍으면 서로 다른 값이 나란히 보여 원인 추적을 방해한다.
            log.info("===FILE_PURGE no candidates (cutoff={})", cutoff);
            return new PurgeResult(0, 0, 0);
        }

        log.info("===FILE_PURGE start count={} cutoff={} dryRun={}",
            candidates.size(), cutoff, properties.isDryRun());

        int ok = 0, fail = 0;
        long totalBytes = 0L;
        for (FileItem item : candidates) {
            try {
                totalBytes += item.getSizeBytes();
                if (properties.isDryRun()) {
                    log.info("===FILE_PURGE dry-run fileId={} original={} path={}",
                        item.getFileId(), item.getOriginalName(), item.getStoredPath());
                    ok++;
                    continue;
                }
                purgeOne(item);
                ok++;
            } catch (Exception ex) {
                fail++;
                log.warn("FILE_PURGE fail fileId={} reason={}",
                    item.getFileId(), ex.getMessage(), ex);
            }
        }

        auditLogger.write(AuditEvent.of("FILE_PURGE_BATCH", "tb_file")
            .withAfter("{\"candidates\":" + candidates.size()
                + ",\"ok\":"      + ok
                + ",\"fail\":"    + fail
                + ",\"bytes\":"   + totalBytes
                + ",\"cutoff\":"  + JsonUtils.quote(cutoff.toString())
                + ",\"dryRun\":"  + properties.isDryRun() + "}")
            .withResult(fail == 0 ? "SUCCESS" : "PARTIAL"));

        log.info("===FILE_PURGE end ok={} fail={} bytes={}", ok, fail, totalBytes);
        return new PurgeResult(ok, fail, totalBytes);
    }

    /**
     * 개별 파일 purge. 물리 파일 3곳 삭제 → DB 행 hard delete → 감사.
     * 물리 삭제가 실패해도 DB 삭제는 하지 않음 (다음 배치 재시도).
     */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    protected void purgeOne(FileItem item) {
        Path upload     = storage.resolveStoredPath(item.getStoredPath());
        Path quarantine = storage.resolveQuarantinePath(item.getStoredPath());
        Path thumbnail  = (item.getThumbnailPath() == null || item.getThumbnailPath().isBlank())
            ? null
            : storage.resolveThumbnailPath(item.getThumbnailPath());

        safeDelete(upload,     "upload");
        safeDelete(quarantine, "quarantine");
        if (thumbnail != null) safeDelete(thumbnail, "thumbnail");

        int affected = fileMapper.hardDelete(item.getFileId());
        if (affected == 0) {
            throw new IllegalStateException("DB hardDelete affected=0 fileId=" + item.getFileId());
        }

        auditLogger.write(AuditEvent.of("FILE_PURGE", "tb_file")
            .withTarget(item.getFileId())
            .withBefore("{\"originalName\":" + JsonUtils.quote(item.getOriginalName())
                + ",\"storedPath\":"    + JsonUtils.quote(item.getStoredPath())
                + ",\"thumbnailPath\":" + JsonUtils.quote(item.getThumbnailPath())
                + ",\"sizeBytes\":"     + item.getSizeBytes()
                + ",\"fileHash\":"      + JsonUtils.quote(item.getFileHash()) + "}")
            .withResult("SUCCESS"));
    }

    private static void safeDelete(Path path, String kind) {
        if (path == null) return;
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("FILE_PURGE deleted kind={} path={}", kind, path);
            }
        } catch (IOException ex) {
            log.warn("FILE_PURGE delete failed kind={} path={} reason={}",
                kind, path, ex.getMessage());
        }
    }

    /** 배치 결과 요약. */
    public record PurgeResult(int ok, int fail, long totalBytes) {}
}
