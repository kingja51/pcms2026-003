package com.gonet.primary.board.category.service;

import com.gonet.primary.board.category.dto.BbsCategory;
import com.gonet.primary.board.category.dto.BbsCategorySaveForm;

import java.util.List;

/**
 * 게시판 카테고리 서비스 — 관리자 CRUD + 사용자/시스템 옵션 조회.
 */
public interface BoardCategoryService {

    /** 게시판의 활성 카테고리 — 사용자 필터 칩 / 글 작성 select. */
    List<BbsCategory> listActive(String bbsMasterId);

    /** 관리자 — 비활성 포함 + 글 개수. */
    List<BbsCategory> listAll(String bbsMasterId);

    BbsCategory get(String categoryId);

    String create(BbsCategorySaveForm form);

    void update(BbsCategorySaveForm form);

    void toggleUse(String categoryId, boolean active);

    void softDelete(String categoryId);
}
