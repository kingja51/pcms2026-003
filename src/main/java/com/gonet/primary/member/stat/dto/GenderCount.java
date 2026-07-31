package com.gonet.primary.member.stat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 성별 ({@code M}/{@code F}/{@code UNKNOWN}) 별 회원수.
 */
@Getter
@Setter
public class GenderCount {

    private String gender;
    private long   total;
}
