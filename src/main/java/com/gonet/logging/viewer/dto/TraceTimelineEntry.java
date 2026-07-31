package com.gonet.logging.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Trace timeline 1행 — 4개 log_* 테이블 결과를 시간순 정렬용 통합 행.
 *
 * <p>{@code source} 로 테이블 종류(access/audit/error/file-download)를 구분하고
 * {@code summary} 에 핵심 정보 한 줄, {@code detailUrl} 로 원본 detail 화면 링크.
 */
@Getter
@AllArgsConstructor
public class TraceTimelineEntry implements Comparable<TraceTimelineEntry> {

    /** access | audit | error | file-download */
    private final String        source;
    private final LocalDateTime at;
    /** 한 줄 요약 — list 화면에서 표시 */
    private final String        summary;
    /** 원본 row 의 detail 페이지 URL */
    private final String        detailUrl;
    /** 스타일 분기용 — INFO / SUCCESS / FAIL / ERROR */
    private final String        severity;

    @Override
    public int compareTo(TraceTimelineEntry o) {
        if (o == null) return -1;
        if (this.at == null)  return  1;
        if (o.at == null)     return -1;
        return this.at.compareTo(o.at);
    }
}
