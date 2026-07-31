package com.gonet.primary.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 수동 발송 폼.
 *
 * <p>{@code recipientUserIdsCsv} = 쉼표 구분 user_seq 다건. 1건만 보낼 때도 같은 필드 사용.
 * 향후 broadcast 옵션이 필요하면 별도 필드 추가 (예: {@code broadcastSiteId}).
 */
@Getter
@Setter
public class NotificationSendForm {

    @NotBlank(message = "수신자 user_seq 를 입력하세요.")
    private String recipientUserIdsCsv;

    /** 알림 타입 — 기본 SYSTEM. */
    @NotBlank
    private String notificationType = "SYSTEM";

    @NotBlank
    @Size(max = 200, message = "제목은 200자 이내")
    private String title;

    @Size(max = 2000, message = "본문은 2000자 이내")
    private String body;

    @Size(max = 500)
    private String linkUrl;
}
