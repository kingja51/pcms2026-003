package com.gonet.primary.member.dormant.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DormantNoticeSearch extends PageRequest {
    private String    memberId;
    private String    stage;        // 30D / 7D / 1D
    private LocalDate sentFrom;
    private LocalDate sentTo;
}
