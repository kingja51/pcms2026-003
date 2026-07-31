package com.gonet.logging.viewer.service;

import com.gonet.common.dto.PageResponse;
import com.gonet.logging.access.dto.AccessLog;
import com.gonet.logging.access.mapper.AccessLogMapper;
import com.gonet.logging.error.dto.ErrorLog;
import com.gonet.logging.error.mapper.ErrorLogMapper;
import com.gonet.logging.file.dto.FileDownloadLog;
import com.gonet.logging.file.mapper.FileDownloadLogMapper;
import com.gonet.logging.log.dto.AuditLog;
import com.gonet.logging.log.dto.LoginLog;
import com.gonet.logging.log.mapper.AuditLogMapper;
import com.gonet.logging.log.mapper.LoginLogMapper;
import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import com.gonet.logging.privacy.mapper.PrivacyAccessLogMapper;
import com.gonet.logging.security.dto.SecurityLog;
import com.gonet.logging.security.mapper.SecurityLogMapper;
import com.gonet.logging.viewer.dto.LogSearch;
import com.gonet.logging.viewer.dto.TraceTimeline;
import com.gonet.logging.viewer.dto.TraceTimelineEntry;
import com.gonet.config.datasource.LoggingDataSourceConfig;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true, transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
public class LogViewServiceImpl extends EgovAbstractServiceImpl implements LogViewService {

    private final AccessLogMapper        accessMapper;
    private final AuditLogMapper         auditMapper;
    private final LoginLogMapper         loginMapper;
    private final FileDownloadLogMapper  fileMapper;
    private final ErrorLogMapper         errorMapper;
    private final PrivacyAccessLogMapper privacyMapper;
    private final SecurityLogMapper      securityMapper;

    public LogViewServiceImpl(AccessLogMapper accessMapper,
                               AuditLogMapper auditMapper,
                               LoginLogMapper loginMapper,
                               FileDownloadLogMapper fileMapper,
                               ErrorLogMapper errorMapper,
                               PrivacyAccessLogMapper privacyMapper,
                               SecurityLogMapper securityMapper) {
        this.accessMapper   = accessMapper;
        this.auditMapper    = auditMapper;
        this.loginMapper    = loginMapper;
        this.fileMapper     = fileMapper;
        this.errorMapper    = errorMapper;
        this.privacyMapper  = privacyMapper;
        this.securityMapper = securityMapper;
    }

    @Override
    public PageResponse<AccessLog> listAccess(LogSearch search) {
        prepare(search);
        long total = accessMapper.countRecent(search);
        List<AccessLog> rows = (total == 0) ? List.of() : accessMapper.findRecent(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public AccessLog getAccess(long id) {
        return accessMapper.findById(id);
    }

    @Override
    public PageResponse<AuditLog> listAudit(LogSearch search) {
        prepare(search);
        long total = auditMapper.countRecent(search);
        List<AuditLog> rows = (total == 0) ? List.of() : auditMapper.findRecent(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public AuditLog getAudit(long id) {
        return auditMapper.findById(id);
    }

    @Override
    public PageResponse<LoginLog> listLogin(LogSearch search) {
        prepare(search);
        long total = loginMapper.countRecent(search);
        List<LoginLog> rows = (total == 0) ? List.of() : loginMapper.findRecent(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public LoginLog getLogin(long id) {
        return loginMapper.findById(id);
    }

    @Override
    public PageResponse<FileDownloadLog> listFileDownload(LogSearch search) {
        prepare(search);
        long total = fileMapper.countRecent(search);
        List<FileDownloadLog> rows = (total == 0) ? List.of() : fileMapper.findRecent(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public FileDownloadLog getFileDownload(long id) {
        return fileMapper.findById(id);
    }

    @Override
    public PageResponse<ErrorLog> listError(LogSearch search) {
        prepare(search);
        long total = errorMapper.countRecent(search);
        List<ErrorLog> rows = (total == 0) ? List.of() : errorMapper.findRecent(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public ErrorLog getError(long id) {
        return errorMapper.findById(id);
    }

    @Override
    public PageResponse<PrivacyAccessLog> listPrivacyAccess(LogSearch search) {
        prepare(search);
        long total = privacyMapper.countRecent(search);
        List<PrivacyAccessLog> rows = (total == 0) ? List.of() : privacyMapper.findRecent(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public PrivacyAccessLog getPrivacyAccess(long id) {
        return privacyMapper.findById(id);
    }

    @Override
    public PageResponse<SecurityLog> listSecurity(LogSearch search) {
        prepare(search);
        long total = securityMapper.countRecent(search);
        List<SecurityLog> rows = (total == 0) ? List.of() : securityMapper.findRecent(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public SecurityLog getSecurity(long id) {
        return securityMapper.findById(id);
    }

    @Override
    public TraceTimeline traceTimeline(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return new TraceTimeline(traceId, 0, 0, 0, 0, Collections.emptyList());
        }
        String tid = traceId.trim();

        List<AccessLog>       accessRows  = accessMapper.findByTraceId(tid);
        List<AuditLog>        auditRows   = auditMapper.findByTraceId(tid);
        List<ErrorLog>        errorRows   = errorMapper.findByTraceId(tid);
        List<FileDownloadLog> fileRows    = fileMapper.findByTraceId(tid);
        List<PrivacyAccessLog> privacyRows  = privacyMapper.findByTraceId(tid);
        List<SecurityLog>     securityRows = securityMapper.findByTraceId(tid);

        List<TraceTimelineEntry> entries = new ArrayList<>();
        for (AccessLog a : accessRows) {
            String summary = (a.getHttpMethod() == null ? "" : a.getHttpMethod() + " ")
                + a.getRequestUri()
                + " → " + a.getStatusCode() + " (" + a.getResponseMs() + "ms)";
            entries.add(new TraceTimelineEntry("access", a.getLoggedAt(), summary,
                "/admin/system/log/access/" + a.getLogAccessId(),
                a.getStatusCode() != null && a.getStatusCode() >= 500 ? "ERROR"
                    : a.getStatusCode() != null && a.getStatusCode() >= 400 ? "FAIL"
                    : "INFO"));
        }
        for (AuditLog au : auditRows) {
            String summary = au.getAction()
                + (au.getTargetEntity() != null ? " · " + au.getTargetEntity() : "")
                + (au.getTargetId() != null ? " · " + au.getTargetId() : "")
                + " → " + au.getResult();
            entries.add(new TraceTimelineEntry("audit", au.getLoggedAt(), summary,
                "/admin/system/log/audit/" + au.getLogAuditId(),
                "SUCCESS".equalsIgnoreCase(au.getResult()) ? "SUCCESS" : "FAIL"));
        }
        for (ErrorLog er : errorRows) {
            String summary = er.getErrorClass()
                + (er.getErrorMessage() != null ? " — " + er.getErrorMessage() : "");
            entries.add(new TraceTimelineEntry("error", er.getLoggedAt(), summary,
                "/admin/system/log/error/" + er.getLogErrorId(),
                "ERROR"));
        }
        for (FileDownloadLog fd : fileRows) {
            String summary = fd.getDownloadType()
                + " · " + (fd.getOriginalName() != null ? fd.getOriginalName() : fd.getFileId())
                + " (" + (fd.getSizeBytes() == null ? 0 : fd.getSizeBytes()) + "B)";
            entries.add(new TraceTimelineEntry("file-download", fd.getDownloadedAt(), summary,
                "/admin/system/log/file-download/" + fd.getLogFileDownloadId(),
                "SUCCESS".equalsIgnoreCase(fd.getResult()) ? "SUCCESS" : "FAIL"));
        }
        for (PrivacyAccessLog pa : privacyRows) {
            String summary = pa.getAccessAction()
                + (pa.getTargetEntity() != null ? " · " + pa.getTargetEntity() : "")
                + (pa.getTargetCount() != null ? " ×" + pa.getTargetCount() : "")
                + (pa.getPiiFields() != null ? " · " + pa.getPiiFields() : "")
                + " → " + pa.getResult();
            entries.add(new TraceTimelineEntry("privacy", pa.getAccessedAt(), summary,
                "/admin/system/log/privacy/" + pa.getLogPrivacyAccessId(),
                "SUCCESS".equalsIgnoreCase(pa.getResult()) ? "SUCCESS"
                    : "DENIED".equalsIgnoreCase(pa.getResult()) ? "FAIL"
                    : "ERROR"));
        }
        for (SecurityLog sec : securityRows) {
            String summary = sec.getEventType()
                + " · severity=" + sec.getSeverity()
                + (sec.getRequestUri() != null ? " · " + sec.getRequestUri() : "");
            String level = "CRITICAL".equalsIgnoreCase(sec.getSeverity()) ? "ERROR"
                : "WARN".equalsIgnoreCase(sec.getSeverity())             ? "FAIL"
                : "INFO";
            entries.add(new TraceTimelineEntry("security", sec.getLoggedAt(), summary,
                "/admin/system/log/security/" + sec.getLogSecurityId(), level));
        }

        Collections.sort(entries);
        return new TraceTimeline(tid,
            accessRows.size(), auditRows.size(), errorRows.size(),
            fileRows.size() + privacyRows.size() + securityRows.size(), entries);
    }

    /** 검색 DTO 정상화 + 안전 캐핑 */
    private static void prepare(LogSearch search) {
        if (search == null) throw new IllegalArgumentException("search required");
        search.normalize();
    }
}
