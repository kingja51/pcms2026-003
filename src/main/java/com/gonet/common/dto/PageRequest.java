package com.gonet.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 공통 페이징·검색·정렬 요청 DTO.
 *
 * <p>도메인별 Search DTO 는 이 클래스를 상속하여 도메인 필터 필드만 추가한다.
 * MyBatis 에서는 {@code #{search.pageSize}} / {@code #{search.offset}} 로 접근.
 *
 * <p>필드 네이밍은 GoPCMS 기존 관례({@code pageSize}) 를 유지.
 *
 * <p><b>H-5 입력 검증 — 2단 방어</b>:
 * <ol>
 *   <li>Bean Validation 어노테이션 ({@code @Min} / {@code @Max} / {@code @Size}) —
 *       컨트롤러에서 {@code @Valid} 사용 시 음수/과대값/긴 키워드를 400 으로 거부 가능.
 *       의도를 코드로 표현하는 게 주 목적.</li>
 *   <li>setter clamp — {@code @Valid} 미부착 컨트롤러나 직접 호출에서도 자동 복구.
 *       이중 방어로 SQL 음수 OFFSET 회귀 영구 차단.</li>
 * </ol>
 */
@Getter
@Setter
@EqualsAndHashCode
public class PageRequest {

    /** 1-based 페이지 번호 */
    @Min(value = 1, message = "page 는 1 이상")
    @Max(value = 1_000_000, message = "page 는 1,000,000 이하")
    private int page = 1;

    /** 페이지당 행 수 (상한 200) */
    @Min(value = 1,   message = "pageSize 는 1 이상")
    @Max(value = 200, message = "pageSize 는 200 이하")
    private int pageSize = 20;

    /** 검색 키워드 (LIKE 대상은 각 도메인 Mapper 에서 결정) */
    @Size(max = 200, message = "keyword 는 200자 이하")
    private String keyword;

    /** 검색 유형 (도메인별 화면에서 선택) — 예: CODE/NAME/ALL */
    @Size(max = 40, message = "searchType 는 40자 이하")
    private String searchType;

    /** 정렬 컬럼 (snake_case 권장) */
    @Size(max = 40, message = "sortBy 는 40자 이하")
    private String sortBy;

    /** 정렬 방향 — 기본 DESC */
    @Size(max = 4, message = "sortDir 는 ASC/DESC")
    private String sortDir = "DESC";

    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.min(Math.max(1, pageSize), 200);
    }

    /**
     * 상한(200) 을 벗어나는 <b>신뢰 내부 호출</b> 전용 — 엑셀 전체 내보내기·드롭다운 전량 로드 등.
     *
     * <p>{@link #setPageSize(int)} 의 200 클램프는 사용자 바인딩 값의 OFFSET 폭탄을 막기 위한 것이라,
     * 서버 코드가 프로그램적으로 1000·2000 을 지정해도 조용히 200 으로 잘려 엑셀에 일부 행만
     * 담기는 데이터 누락이 발생했다. 이 메서드는 그 클램프를 우회하되 100,000 상한으로 한 번 더 방어.
     * 컨트롤러가 사용자 입력을 여기에 그대로 넘기지 말 것.
     */
    public void setPageSizeUnbounded(int pageSize) {
        this.pageSize = Math.min(Math.max(1, pageSize), 100_000);
    }

    /** MyBatis LIMIT OFFSET 계산 — 음수 회피 */
    public int getOffset() {
        return Math.max(0, (page - 1) * pageSize);
    }
}
