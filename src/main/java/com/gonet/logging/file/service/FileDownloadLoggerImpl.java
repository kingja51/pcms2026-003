package com.gonet.logging.file.service;

import com.gonet.common.audit.AuditContext;
import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.file.dto.FileDownloadLog;
import com.gonet.logging.file.mapper.FileDownloadLogMapper;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * {@link FileDownloadLogger} 구현 — log_file_download INSERT.
 *
 * <p>트랜잭션 정책:
 * <ul>
 *   <li>{@link Propagation#REQUIRES_NEW} — 호출자(primary TX) 와 완전 분리.
 *       파일 응답 트랜잭션이 어떤 이유로 롤백되어도 다운로드 이력은 유지.</li>
 *   <li>{@link LoggingDataSourceConfig#TRANSACTION_MGR} 명시 — 3-DB 환경에서
 *       primary 가 아닌 logging 커넥션을 사용.</li>
 *   <li>내부 helper 메서드에는 붙이지 않음 — self-invocation 은 프록시 우회.</li>
 * </ul>
 */
@Service("fileDownloadLogger")
public class FileDownloadLoggerImpl extends EgovAbstractServiceImpl
    implements FileDownloadLogger {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadLoggerImpl.class);

    private final FileDownloadLogMapper mapper;

    public FileDownloadLoggerImpl(FileDownloadLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW,
        transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
    public void logSingle(FileItem f, DownloadType type, long sentBytes, HttpServletRequest req) {
        if (f == null) return;
        // SINGLE/ADMIN 타입도 fileGroupId 를 함께 보관 — 그룹 단위 이력 조회·집계 호환성 확보
        record(type, f.getFileId(), f.getFileGroupId(),
            f.getOriginalName(), f.getExtension(), sentBytes, req);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW,
        transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
    public void logGroup(String fileGroupId, long zipBytes, HttpServletRequest req) {
        record(DownloadType.GROUP_ZIP, null, fileGroupId,
            null, null, zipBytes, req);
    }

    /**
     * 실제 INSERT 수행. {@link #logSingle}/{@link #logGroup} 의 REQUIRES_NEW TX 를 상속하며,
     * 같은 클래스 내부 호출이므로 여기에는 {@code @Transactional} 을 붙이지 않는다
     * (self-invocation 은 Spring AOP 프록시 우회 → 속성 무시).
     *
     * <p>예외는 로그만 남기고 삼킨다. 로깅 실패가 파일 다운로드 응답을 막아선 안 되기 때문.
     * 단, REQUIRES_NEW TX 는 이 메서드 throw 시에도 롤백 대상이 되므로 try/catch 로 감싼다.
     */
    private void record(DownloadType type,
                         String fileId,
                         String fileGroupId,
                         String originalName,
                         String extension,
                         long sizeBytes,
                         HttpServletRequest req) {
        try {
            FileDownloadLog entry = new FileDownloadLog();
            entry.setFileId(fileId);
            entry.setFileGroupId(fileGroupId);
            entry.setDownloadType(type.name());
            entry.setOriginalName(originalName);
            entry.setExtension(extension);
            entry.setSizeBytes(sizeBytes);

            populateActor(entry);
            populateRequest(entry, req);
            entry.setResult("SUCCESS");
            entry.setDownloadedAt(LocalDateTime.now());
            entry.setCreatedBy(entry.getActorUserId());
            entry.setCreatedIp(entry.getClientIp());
            mapper.insert(entry);
        } catch (Exception ex) {
            log.warn("FILE_DOWNLOAD_LOG_FAIL type={} fileId={} reason={}",
                type, fileId, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------

    private static void populateActor(FileDownloadLog entry) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
            || "anonymousUser".equals(auth.getPrincipal())) {
            entry.setActorUserId(AuditContext.ANONYMOUS);
            entry.setActorUserType(AuditContext.ANONYMOUS);
            return;
        }
        if (auth.getPrincipal() instanceof CustomUserDetails ud) {
            entry.setActorUserId(ud.getUserId());
            entry.setActorUserType(ud.getUserType());
            entry.setActorLoginId(ud.getUsername());
        } else {
            entry.setActorUserId(auth.getName());
        }
    }

    private static void populateRequest(FileDownloadLog entry, HttpServletRequest req) {
        if (req == null) return;
        entry.setRequestUri(req.getRequestURI());
        entry.setClientIp(com.gonet.common.util.IpUtils.clientIp(req));
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
        entry.setUserAgent(ua);
        entry.setTraceId(org.slf4j.MDC.get("traceId"));
    }
}
