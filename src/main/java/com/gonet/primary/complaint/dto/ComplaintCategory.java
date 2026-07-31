package com.gonet.primary.complaint.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class ComplaintCategory {

    private String        categoryId;
    private String        complaintMasterId;
    private String        categoryCode;
    private String        categoryName;
    private String        description;
    private int           sortOrder;
    private String        useYn;
    private String        deleteYn;
    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
    private String        updatedBy;
    private String        updatedIp;
    private LocalDateTime updatedAt;
}
