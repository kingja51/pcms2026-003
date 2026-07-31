package com.gonet.primary.banner.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_banner 엔티티 — 사이트별 배너.
 *
 * <p>위치(banner_location) 는 운영 자유 문자열 — 레이아웃 측에서 같은 location 을 가진
 * 활성 배너를 일괄 노출. 운영 권장 값: {@code HEADER}, {@code FOOTER}, {@code SIDE}, {@code MAIN_TOP}.
 *
 * <p>표시용 JOIN 필드: {@code siteCode}, {@code siteName}.
 */
@Getter
@Setter
public class Banner extends BaseEntity implements SoftDeletable, UseFlagged {

    private String bannerId;
    private String siteId;
    private String bannerTitle;
    private String bannerLocation;

    /** 첨부 file_group_id (FG0_…) — banner 이미지 업로드 그룹. */
    private String fileGroupId;
    private String altText;

    private String linkUrl;
    private String linkTarget;

    private LocalDateTime showFrom;
    private LocalDateTime showTo;

    private int sortOrder;

    private String useYn;
    private String deleteYn;

    private String siteCode;
    private String siteName;

    /** 조회 전용 — 그룹의 첫 이미지 file_id (findActiveBySiteId 서브쿼리). 사용자 노출용. */
    private String imageFileId;
}
