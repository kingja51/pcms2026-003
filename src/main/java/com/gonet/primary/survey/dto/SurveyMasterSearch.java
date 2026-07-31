package com.gonet.primary.survey.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurveyMasterSearch extends PageRequest {

    private String siteId;
    private String menuId;
    private String useYn;
}
