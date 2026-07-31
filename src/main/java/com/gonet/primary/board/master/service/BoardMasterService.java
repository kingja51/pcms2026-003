package com.gonet.primary.board.master.service;

import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.dto.BbsMasterSaveForm;
import com.gonet.primary.board.master.dto.BbsMasterSearch;

import java.util.List;

/**
 * 게시판 마스터 서비스 — 관리자 CRUD 진입점.
 *
 * <p>책임:
 * <ul>
 *   <li>사이트 내 bbs_code 중복 검증 (등록/수정 모두)</li>
 *   <li>file_size_max byte 환산 (form: MB → DTO: byte)</li>
 *   <li>감사 이벤트 발행 (BBS_MASTER_CREATE/UPDATE/DELETE/USE_TOGGLE)</li>
 *   <li>soft delete + 사용중지 토글 분리</li>
 * </ul>
 */
public interface BoardMasterService {

    List<BbsMaster> search(BbsMasterSearch search);

    int count(BbsMasterSearch search);

    BbsMaster get(String bbsMasterId);

    /** 사용자 라우팅용 — (siteCode, bbsCode) 로 마스터 해석. 없으면 null. */
    BbsMaster getBySiteCodeAndBbsCode(String siteCode, String bbsCode);

    /** @return 신규 bbs_master_id (UUID v7). */
    String create(BbsMasterSaveForm form);

    void update(BbsMasterSaveForm form);

    /** {@code use_yn} 만 변경 — 본문/설정 보존. */
    void toggleUse(String bbsMasterId, boolean active);

    void softDelete(String bbsMasterId);
}
