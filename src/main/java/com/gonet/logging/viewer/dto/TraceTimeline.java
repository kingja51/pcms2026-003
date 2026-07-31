package com.gonet.logging.viewer.dto;

import lombok.Getter;

import java.util.List;

/**
 * trace_id 1건의 통합 결과 — 4개 log_* 테이블 카운트 + 시간순 timeline.
 */
@Getter
public class TraceTimeline {

    private final String traceId;
    private final int    accessCount;
    private final int    auditCount;
    private final int    errorCount;
    private final int    fileDownloadCount;
    private final List<TraceTimelineEntry> entries;

    public TraceTimeline(String traceId,
                         int accessCount, int auditCount, int errorCount, int fileDownloadCount,
                         List<TraceTimelineEntry> entries) {
        this.traceId           = traceId;
        this.accessCount       = accessCount;
        this.auditCount        = auditCount;
        this.errorCount        = errorCount;
        this.fileDownloadCount = fileDownloadCount;
        this.entries           = entries;
    }

    public int totalCount() {
        return accessCount + auditCount + errorCount + fileDownloadCount;
    }

    public boolean isEmpty() {
        return totalCount() == 0;
    }
}
