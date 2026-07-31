package com.gonet.primary.member.stat.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 일별 회원 변동 통계 1행.
 *
 * <p>의미는 호출자에 따라 다름:
 * <ul>
 *   <li>{@code joinCount}     — 그 날 가입한 회원수 (tb_member.created_at)</li>
 *   <li>{@code dormantCount}  — 그 날 휴면 전환 (tb_member_dormant.created_at)</li>
 *   <li>{@code withdrawCount} — 그 날 탈퇴 (tb_member_withdraw.withdraw_at)</li>
 * </ul>
 *
 * <p>1쿼리에서 LEFT JOIN 으로 묶기는 비용이 커서 메서드별로 3쿼리를 따로 친 뒤
 * Service 단에서 합쳐 단일 DTO 컬렉션으로 노출한다.
 */
@Getter
@Setter
public class DailyMemberStat {

    private LocalDate statDate;
    private long      joinCount;
    private long      dormantCount;
    private long      withdrawCount;
}
