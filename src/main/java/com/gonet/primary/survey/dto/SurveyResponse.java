package com.gonet.primary.survey.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class SurveyResponse extends BaseEntity {

    private String        responseId;
    private String        surveyId;
    private String        memberId;
    private String        clientIp;
    private LocalDateTime submittedAt;

    private List<SurveyAnswer> answers;
    private String memberLoginId;
    private String memberName;
}
