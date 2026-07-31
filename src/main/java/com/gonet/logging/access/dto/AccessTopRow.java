package com.gonet.logging.access.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 접근 통계 TOP-N 차트 공통 행 DTO.
 *
 * <p>11종 차트가 모두 같은 구조 — {label, value(주측정값), valueExtra(보조 측정값)}.
 *
 * <ul>
 *   <li>{@code label}      — 차트 X축 라벨 (IP / URI / referer / user-agent / file-name 등)</li>
 *   <li>{@code value}      — 정렬 기준 값 (count / sum-bytes / distinct-count)</li>
 *   <li>{@code valueExtra} — 보조 표시값 (avg-response-ms 등 — 없으면 null)</li>
 * </ul>
 */
@Getter
@Setter
public class AccessTopRow {

    private String  label;
    private Long    value;
    private Double  valueExtra;
}
