package com.gonet.primary.complaint.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComplaintArticleSearch extends PageRequest {

    private String complaintMasterId;
    private String categoryId;
    private String status;
    private String writerName;
    private String complaintNo;
    /** show_in_list='N' 일 때 본인 민원만 조회하기 위한 member_id 필터 */
    private String writerMemberId;
}
