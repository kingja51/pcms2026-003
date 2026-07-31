package com.gonet.primary.banner.mapper;

import com.gonet.primary.banner.dto.Banner;
import com.gonet.primary.banner.dto.BannerSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@EgovMapper
public interface BannerMapper {

    List<Banner> findList(@Param("search") BannerSearch search);

    int countList(@Param("search") BannerSearch search);

    Banner findById(@Param("bannerId") String bannerId);

    /**
     * 사용자 화면용 — 사이트의 모든 활성 배너. location 은 호출 측에서 그룹핑.
     * use_yn='Y' AND show_from <= now <= show_to.
     */
    List<Banner> findActiveBySiteId(@Param("siteId") String siteId,
                                      @Param("now") LocalDateTime now);

    /**
     * 사이트 + 위치 내 현재 최대 sort_order — 신규 등록 시 max+1 부여용.
     * 배너는 location 별로 정렬되므로 (siteId, bannerLocation) 범위로 평가.
     */
    Integer maxSortOrderBySiteAndLocation(@Param("siteId") String siteId,
                                            @Param("bannerLocation") String bannerLocation);

    int insert(Banner banner);

    int update(Banner banner);

    int updateUseYn(@Param("bannerId") String bannerId,
                     @Param("useYn") String useYn);

    int softDelete(@Param("bannerId") String bannerId);

    /**
     * Soft-delete retention — cutoff 이전 (updated_at &lt;= cutoff) 의
     * delete_yn='Y' 행을 hard delete. 호출 측에서 도메인별 트랜잭션을 연다.
     */
    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** Soft-delete retention — dry-run 카운트. DELETE 없이 후보 행 수만. */
    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
