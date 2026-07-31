package com.gonet.primary.survey.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SurveyQuestion extends BaseEntity implements SoftDeletable {

    private String  questionId;
    private String  surveyId;
    private String  questionText;
    private String  questionType;
    private String  requiredYn;
    private Integer scaleMin;
    private Integer scaleMax;
    private int     sortOrder;
    private String  deleteYn;

    private List<SurveyOption> options;

    public QuestionType typeEnum() {
        return QuestionType.safeParse(questionType);
    }

    public boolean isRequired() {
        return "Y".equalsIgnoreCase(requiredYn);
    }
}
