package com.gonet.primary.member.dormant.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_member_dormant_notice — 회원 휴면 전환 안내 메일 발송 이력.
 *
 * <p>UNIQUE (member_id, stage) — 같은 단계 중복 발송 차단. stage 는 30D/7D/1D.
 * 본 도메인은 immutable audit — soft delete 컬럼 없음.
 */
@Getter
@Setter
public class DormantNotice {

    private String        noticeId;
    private String        memberId;
    private String        stage;        // 30D / 7D / 1D
    private LocalDateTime sentAt;
    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
}
