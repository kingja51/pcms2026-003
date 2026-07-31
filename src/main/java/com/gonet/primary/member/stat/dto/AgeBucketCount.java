package com.gonet.primary.member.stat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 연령대 ({@code ageBucket} = "10"/"20"/.../"60+"/"UNKNOWN") 별 회원수.
 *
 * <p>SQL 측에서 birth_year 평문(§0.27) 으로부터 동적 계산.
 * birth_year null 또는 4자리 숫자 형식이 아니면 UNKNOWN 으로 분류.
 */
@Getter
@Setter
public class AgeBucketCount {

    private String ageBucket;
    private long   total;
}
