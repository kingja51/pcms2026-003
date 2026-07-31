package com.gonet.primary.complaint.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComplaintAnswer extends BaseEntity {

    private String        answerId;
    private String        articleId;

    private String        answererId;
    private String        answererType;
    private String        answererName;
    private String        answererDept;

    private String        content;
    private String        fileGroupId;

    private String        isFinal;

    private String        deleteYn;
}
