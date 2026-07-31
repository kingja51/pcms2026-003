package com.gonet.primary.complaint.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.crypto.Encrypt;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class ComplaintArticle extends BaseEntity {

    private String          articleId;
    private String          complaintMasterId;
    private String          categoryId;
    private String          categoryName;   // JOIN
    private String          fileGroupId;

    // 작성자 식별
    private String          writerAuthType;
    private String          writerMemberId;
    private String          writerDiHash;
    private String          writerOauthProvider;
    private String          writerOauthUidHash;
    private String          writerName;

    @Encrypt
    private String          writerContactEnc;  // AES-GCM

    // 접수번호
    private String          complaintNo;

    // 내용
    private String          title;
    private String          content;
    private String          clientIp;

    // 상태
    private String          status;

    // SLA
    private LocalDateTime   answerDueAt;
    private LocalDateTime   answeredAt;
    private LocalDateTime   closedAt;

    // 집계
    private int             answerCount;

    private String          deleteYn;
}
