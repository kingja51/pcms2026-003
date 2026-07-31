package com.gonet.primary.board.master.mapper;

import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.dto.BbsMasterSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_bbs_master CRUD Mapper.
 *
 * <p>관리자 + 사용자 화면 공용. soft-delete 미노출 + 검색·정렬·페이징 +
 * 사이트 내 코드 중복 검사 + 사용자 라우팅용 (siteCode, bbsCode) 해석.
 */
@EgovMapper
public interface BbsMasterMapper {

    List<BbsMaster> findList(@Param("search") BbsMasterSearch search);

    int countList(@Param("search") BbsMasterSearch search);

    BbsMaster findById(@Param("bbsMasterId") String bbsMasterId);

    /**
     * 사용자 화면 라우팅용 — (siteCode, bbsCode) 로 마스터 해석. delete_yn='N' 만,
     * use_yn 은 무관 (사용중지 게시판 직링크 시 503 응답을 위해 호출 측에서 검사).
     */
    BbsMaster findBySiteCodeAndBbsCode(@Param("siteCode") String siteCode,
                                         @Param("bbsCode") String bbsCode);

    /**
     * 사이트 내 bbs_code 중복 체크. 등록 시 excludeId=null, 수정 시 본인 제외.
     */
    int existsByCode(@Param("siteId") String siteId,
                      @Param("bbsCode") String bbsCode,
                      @Param("excludeId") String excludeId);

    int insert(BbsMaster master);

    int update(BbsMaster master);

    /** 사용중지/재사용 토글. */
    int updateUseYn(@Param("bbsMasterId") String bbsMasterId,
                     @Param("useYn") String useYn);

    int softDelete(@Param("bbsMasterId") String bbsMasterId);

    // ------------------------------------------------------------------
    // Soft-delete retention
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
