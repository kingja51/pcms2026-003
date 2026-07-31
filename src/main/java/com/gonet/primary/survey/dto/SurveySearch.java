package com.gonet.primary.survey.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurveySearch extends PageRequest {

    /** master 단위 필터 (관리자: 마스터 selector). */
    private String surveyMasterId;

    /** master.site_id 로 우회 필터 (사용자 측 SiteContext 가 사용). */
    private String siteId;

    private String status;
    private String useYn;
}
