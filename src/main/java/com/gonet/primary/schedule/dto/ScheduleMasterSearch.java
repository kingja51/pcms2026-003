package com.gonet.primary.schedule.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleMasterSearch extends PageRequest {

    private String siteId;
    private String menuId;
    private String useYn;
}
