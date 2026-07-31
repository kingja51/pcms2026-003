package com.gonet.primary.survey.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurveyOption extends BaseEntity implements SoftDeletable {

    private String optionId;
    private String questionId;
    private String optionText;
    private String optionValue;
    private int    sortOrder;
    private String deleteYn;

    /** 통계용 — Service 가 채울 수 있음. */
    private long count;
    private double percentage;
}
