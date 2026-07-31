package com.gonet.primary.survey.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurveyAnswer extends BaseEntity {

    private String  answerId;
    private String  responseId;
    private String  questionId;
    private String  optionId;
    private String  answerText;
    private Integer answerNumber;

    private String questionText;
    private String optionText;
}
