package com.gonet.logging.viewer.service;

import com.gonet.common.dto.PageResponse;
import com.gonet.logging.access.dto.AccessLog;
import com.gonet.logging.error.dto.ErrorLog;
import com.gonet.logging.file.dto.FileDownloadLog;
import com.gonet.logging.log.dto.AuditLog;
import com.gonet.logging.log.dto.LoginLog;
import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import com.gonet.logging.security.dto.SecurityLog;
import com.gonet.logging.viewer.dto.LogSearch;

/**
 * log_* 테이블 viewer Service — 5 종 테이블 조회 진입점.
 *
 * <p>모든 list 메서드는 {@link PageResponse} 로 페이징(기본 100건/page).
 * 상세는 단건 조회.
 */
public interface LogViewService {

    PageResponse<AccessLog>       listAccess(LogSearch search);
    AccessLog                     getAccess(long id);

    PageResponse<AuditLog>        listAudit(LogSearch search);
    AuditLog                      getAudit(long id);

    PageResponse<LoginLog>        listLogin(LogSearch search);
    LoginLog                      getLogin(long id);

    PageResponse<FileDownloadLog> listFileDownload(LogSearch search);
    FileDownloadLog               getFileDownload(long id);

    PageResponse<ErrorLog>        listError(LogSearch search);
    ErrorLog                      getError(long id);

    PageResponse<PrivacyAccessLog> listPrivacyAccess(LogSearch search);
    PrivacyAccessLog               getPrivacyAccess(long id);

    PageResponse<SecurityLog>      listSecurity(LogSearch search);
    SecurityLog                    getSecurity(long id);

    /**
     * trace_id 로 4개 log_* 테이블에서 같은 요청의 모든 행을 조회 후
     * 시간순으로 통합 timeline 반환.
     */
    com.gonet.logging.viewer.dto.TraceTimeline traceTimeline(String traceId);
}
