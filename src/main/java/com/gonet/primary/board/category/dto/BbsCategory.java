package com.gonet.primary.board.category.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_bbs_category — 게시판 내부 카테고리(서브분류).
 *
 * <p>(bbs_master_id, category_code) 가 사이트별 게시판 안에서 UNIQUE.
 * 글당 1개 카테고리 매핑(tb_bbs_article.category_id) — 1:N.
 */
@Getter
@Setter
public class BbsCategory extends BaseEntity implements SoftDeletable, UseFlagged {

    private String  categoryId;
    private String  bbsMasterId;
    private String  categoryCode;
    private String  categoryName;
    private int     sortOrder;
    private String  useYn;
    private String  deleteYn;

    /** 게시판 식별 — 표시용 JOIN 필드. */
    private String bbsCode;
    private String bbsName;
    private String siteId;
    private String siteCode;

    /** 카테고리에 속한 PUBLISHED 글 수 — 목록 칩 카운트용 (옵셔널). */
    private Integer articleCount;
}
