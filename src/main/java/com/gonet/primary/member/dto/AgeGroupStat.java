package com.gonet.primary.member.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 연령대 통계 1행.
 *
 * <p>{@code ageGroupStart} — 연령대 시작 (0/10/20/30/...). NULL 은 "미입력" 분류.
 * 연령대 계산은 통계 시점에 동적 — {@code FLOOR((NOW().year - birth_year) / 10) * 10}.
 */
@Getter
@Setter
public class AgeGroupStat {
    private Integer ageGroupStart;   // 0/10/20/30/40/50/60/70/80+, null = birth_year 미입력
    private long    memberCount;
}
