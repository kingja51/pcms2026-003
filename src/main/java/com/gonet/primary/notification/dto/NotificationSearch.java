package com.gonet.primary.notification.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 알림 검색 — 회원 인박스 / 관리자 통합 list 모두에서 사용.
 *
 * <p>{@code recipientUserId} 가 null 이면 (관리자) 전체 알림 대상,
 * non-null 이면 (회원) 본인 인박스 한정. Service 에서 인증 사용자 강제 주입.
 */
@Getter
@Setter
public class NotificationSearch extends PageRequest {

    /** 회원 인박스 — 본인 user_seq. 관리자 통합 list 는 null. */
    private String recipientUserId;

    /** 알림 타입 필터 — null 이면 전체. */
    private String notificationType;

    /** 'Y'/'N'/null — 읽음 필터. */
    private String readYn;
}
