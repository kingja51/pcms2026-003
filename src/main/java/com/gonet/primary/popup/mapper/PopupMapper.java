package com.gonet.primary.popup.mapper;

import com.gonet.primary.popup.dto.Popup;
import com.gonet.primary.popup.dto.PopupSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_popup CRUD Mapper.
 *
 * <p>관리자 화면용 검색·페이징 + 사용자 화면용 활성 팝업 조회.
 * 활성 조건은 {@link #findActiveBySiteId} 의 SQL 에 인라인 — 요일/시각 필터는
 * Service 단에서 추가 평가.
 */
@EgovMapper
public interface PopupMapper {

    List<Popup> findList(@Param("search") PopupSearch search);

    int countList(@Param("search") PopupSearch search);

    Popup findById(@Param("popupId") String popupId);

    /**
     * 사용자 화면용 — 사이트별 활성 팝업 (use_yn='Y' AND show_from <= now <= show_to).
     * 요일/시각 필터는 Service 에서 후처리.
     *
     * <p>정렬: sort_order ASC, created_at DESC.
     */
    List<Popup> findActiveBySiteId(@Param("siteId") String siteId,
                                     @Param("now") LocalDateTime now);

    /**
     * 사이트 내 현재 최대 sort_order — 신규 등록 시 max+1 부여용.
     * soft-deleted 포함해서 가장 큰 값을 반환 (재사용 회피). 그룹이 비어있으면 NULL.
     */
    Integer maxSortOrderBySite(@Param("siteId") String siteId);

    int insert(Popup popup);

    int update(Popup popup);

    int updateUseYn(@Param("popupId") String popupId,
                     @Param("useYn") String useYn);

    int softDelete(@Param("popupId") String popupId);

    /**
     * Soft-delete retention — cutoff 이전 (updated_at &lt;= cutoff) 의
     * delete_yn='Y' 행을 hard delete. 호출 측에서 도메인별 트랜잭션을 연다.
     */
    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** Soft-delete retention — dry-run 카운트. DELETE 없이 후보 행 수만. */
    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
