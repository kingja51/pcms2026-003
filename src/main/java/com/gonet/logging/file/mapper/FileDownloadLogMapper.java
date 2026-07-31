package com.gonet.logging.file.mapper;

import com.gonet.logging.file.dto.FileDownloadLog;
import com.gonet.logging.viewer.dto.LogSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * log_file_download CRUD — logging DB.
 */
@EgovMapper
public interface FileDownloadLogMapper {

    int insert(FileDownloadLog log);

    /** 파일별 최근 이력 — 상세 페이지에서 사용. */
    List<FileDownloadLog> findRecentByFileId(@Param("fileId") String fileId,
                                               @Param("limit") int limit);

    /** 그룹 ZIP 이력. */
    List<FileDownloadLog> findRecentByGroupId(@Param("fileGroupId") String fileGroupId,
                                                @Param("limit") int limit);

    int countByFileId(@Param("fileId") String fileId);

    /** 관리자 viewer — 페이징 + keyword/날짜 검색 */
    List<FileDownloadLog> findRecent(@Param("search") LogSearch search);

    /** 페이징용 총 건수 — findRecent 와 동일한 WHERE */
    long countRecent(@Param("search") LogSearch search);

    FileDownloadLog findById(@Param("id") long id);

    /** trace_id 로 같은 요청의 모든 다운로드 이력 조회 */
    List<FileDownloadLog> findByTraceId(@Param("traceId") String traceId);
}
