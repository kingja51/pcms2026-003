package com.gonet.primary.system.mail.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailTemplateSearch extends PageRequest {

    private String useYn;

    public MailTemplateSearch() {
        setPageSize(50);
    }
}
