package com.gonet.primary.member.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MemberPasswordHistorySearch extends PageRequest {
    private String    memberId;
    private LocalDate changedFrom;
    private LocalDate changedTo;
}
