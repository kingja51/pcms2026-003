package com.gonet.logging.access.service;

import com.gonet.logging.access.dto.AccessTopRow;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * /admin/system/access-top20 화면 — 11종 TOP-20 통계 진입점.
 *
 * <p>{@link #all(LocalDate, LocalDate, int, int)} 가 11 차트의 데이터를
 * 한 번에 묶어 반환 — 화면 렌더링 1회 호출로 충분.
 */
public interface AccessTopService {

    /** 11 차트 데이터를 키 → rows 매핑으로 일괄 반환. */
    Map<String, List<AccessTopRow>> all(LocalDate from, LocalDate to,
                                          int limit, int slowMinCalls);
}
