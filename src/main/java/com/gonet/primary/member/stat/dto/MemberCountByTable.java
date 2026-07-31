package com.gonet.primary.member.stat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 회원 라이프사이클 3 테이블 각각의 카운트.
 *
 * <p>{@code tableName} 은 {@code tb_member} / {@code tb_member_dormant} / {@code tb_member_withdraw}.
 * 화면에서는 한국어 라벨로 매핑.
 */
@Getter
@Setter
public class MemberCountByTable {

    private String tableName;
    private long   total;
}
