package com.gonet.logging.viewer.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * log_* 테이블 viewer 공통 검색 조건.
 *
 * <p>모든 log_* 화면이 같은 폼을 공유한다 — keyword + 날짜 범위 + 페이징.
 *
 * <p>{@link PageRequest} 상속:
 * <ul>
 *   <li>{@code page} — 1-based 페이지 번호 (기본 1)</li>
 *   <li>{@code pageSize} — 페이지당 행 수 (기본 100, 상한 200)</li>
 *   <li>{@code keyword} — LIKE 대상 (도메인별 컬럼 차이는 mapper 가 결정)</li>
 *   <li>{@code getOffset()} — MyBatis LIMIT/OFFSET 계산</li>
 * </ul>
 *
 * <p>날짜 미입력 시 기본 14일 — {@link #normalize()} 가 채움.
 */
@Getter
@Setter
public class LogSearch extends PageRequest {

    /** 페이지당 기본 100건 — log_* 화면 정책 */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /** 날짜 from (inclusive) — 비우면 14일 전 */
    private LocalDate dateFrom;

    /** 날짜 to (inclusive) — 비우면 오늘 */
    private LocalDate dateTo;

    public LogSearch() {
        // PageRequest 기본 pageSize(20) 을 log_* 정책 기본(100) 으로 override.
        // 사용자가 ?pageSize=50 으로 명시 전달하면 setter 가 덮어씀.
        super.setPageSize(DEFAULT_PAGE_SIZE);
    }

    /** 비워있으면 기본값(14일) 으로 fill — Service 에서 호출 */
    public void normalize() {
        if (dateTo == null)   dateTo   = LocalDate.now();
        if (dateFrom == null) dateFrom = dateTo.minusDays(13);
        String kw = getKeyword();
        if (kw != null && kw.isBlank()) setKeyword(null);
    }
}
