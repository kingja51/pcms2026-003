package com.gonet.primary.survey.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_survey_master 엔티티 — 설문 마스터 (얇은 owner).
 *
 * <p>역할: 사이트/메뉴 컨텍스트 + 설문 그룹 헤더. 실제 설문 데이터(제목/기간/상태/문항 등) 는
 * {@link Survey} (1:N detail) 에 보관.
 */
@Getter
@Setter
public class SurveyMaster extends BaseEntity implements SoftDeletable, UseFlagged {

    private String surveyMasterId;
    private String siteId;
    private String menuId;
    private String masterTitle;
    private String masterContent;
    private String useYn;
    private String deleteYn;

    // tb_site JOIN 으로 채워지는 노출 필드 — INSERT/UPDATE 대상 아님
    private String siteCode;
    private String siteName;
    private String layoutPath;
}
