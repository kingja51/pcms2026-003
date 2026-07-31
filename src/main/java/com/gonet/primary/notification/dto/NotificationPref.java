package com.gonet.primary.notification.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_notification_pref — 사용자별 알림 채널 preference.
 *
 * <p>UNIQUE {@code (user_id, notification_type)}. {@code notification_type='ALL'} 은
 * 타입별 row 가 없을 때 적용되는 기본값 — 운영자가 사용자 단위 ALL 행만 두고 타입별
 * 상세 행은 두지 않는 식으로 운영 가능.
 *
 * <p>resolve 우선순위 (Service 단): 타입별 row → ALL row → hard-coded default(Y/Y).
 */
@Getter
@Setter
public class NotificationPref extends BaseEntity {

    private String prefId;
    private String userId;
    private String notificationType;
    private String channelInappYn;
    private String channelEmailYn;

    public boolean isInappEnabled() { return !"N".equalsIgnoreCase(channelInappYn); }
    public boolean isEmailEnabled() { return !"N".equalsIgnoreCase(channelEmailYn); }
}
