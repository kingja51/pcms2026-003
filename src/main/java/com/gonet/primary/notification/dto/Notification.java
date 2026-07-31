package com.gonet.primary.notification.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_notification 엔티티 — 사용자별 인박스 알림.
 *
 * <p>다채널의 경우 발송 이력은 {@code tb_noti_send} 가 담당하고, 본 엔티티는 in-app
 * 인박스 1건을 표현한다. 수신자(recipient_user_id)는 회원/직원/관리자 공통 user_seq.
 *
 * <p>표시용 JOIN 필드(없으면 null): {@code recipientLoginId}, {@code recipientName}.
 */
@Getter
@Setter
public class Notification extends BaseEntity implements SoftDeletable {

    private String        notificationId;
    private String        recipientUserId;
    private String        notificationType;
    private String        title;
    private String        body;
    private String        linkUrl;
    private String        relatedEntity;
    private String        relatedId;
    private String        readYn;
    private LocalDateTime readAt;
    private String        deleteYn;

    /** 표시 — v_user_login JOIN 결과 (관리자 통합 list 에서만 의미). */
    private String recipientLoginId;

    public NotificationType typeEnum() {
        return NotificationType.safeParse(notificationType);
    }

    public boolean isRead() { return "Y".equalsIgnoreCase(readYn); }
}
