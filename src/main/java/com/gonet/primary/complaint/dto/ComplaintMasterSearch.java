package com.gonet.primary.complaint.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComplaintMasterSearch extends PageRequest {

    private String siteId;
    private String useYn;
}
