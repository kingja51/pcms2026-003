package com.gonet.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 공통 페이징 응답 DTO.
 *
 * <p>MngController 는 Thymeleaf model 에 {@code rows/total/totalPages} 를 직접 넣는 방식을
 * 기존처럼 사용할 수 있고, ApiController 는 본 래퍼를 JSON 으로 반환한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private int     page;
    private int     pageSize;
    private long    totalElements;
    private int     totalPages;

    public static <T> PageResponse<T> of(List<T> content, int page, int pageSize, long totalElements) {
        int totalPages = pageSize <= 0 ? 0
            : (int) Math.ceil((double) totalElements / pageSize);
        return PageResponse.<T>builder()
                .content(content)
                .page(page)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    public boolean hasNext()     { return page < totalPages; }
    public boolean hasPrevious() { return page > 1; }

    /**
     * 메모리 상의 전체 목록을 페이지 단위로 잘라 응답으로 변환.
     * 소규모 결과셋(학과 교수진·직원·연구실·수업계획서 등)에서 이미 필터된 전체 리스트를
     * 재조회 없이 페이징할 때 사용. page 는 1-base, size ≤ 0 이면 20 으로 보정.
     */
    public static <T> PageResponse<T> paginate(List<T> all, int page, int size) {
        List<T> src = (all == null) ? List.of() : all;
        int s = size <= 0 ? 20 : size;
        int p = Math.max(1, page);
        int from = Math.min((p - 1) * s, src.size());
        int to   = Math.min(from + s, src.size());
        return of(src.subList(from, to), p, s, src.size());
    }
}
