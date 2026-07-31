package com.gonet.primary.notification.service;

import com.gonet.primary.notification.dto.NotificationPref;
import com.gonet.primary.notification.dto.NotificationPrefForm;
import com.gonet.primary.notification.dto.NotificationType;

import java.util.List;

/**
 * 알림 채널 preference 서비스.
 *
 * <p>resolve 우선순위 — 타입별 row → ALL row → hard-coded default(in-app=Y, email=Y).
 * 트리거 측은 {@link #isEmailEnabled} / {@link #isInappEnabled} 만 호출.
 */
public interface NotificationPrefService {

    List<NotificationPref> findByUser(String userId);

    /** 회원 본인 / 관리자 강제 변경 공통. 입력 폼은 ALL/타입별 모두 처리. */
    void save(NotificationPrefForm form);

    boolean isInappEnabled(String userId, NotificationType type);

    boolean isEmailEnabled(String userId, NotificationType type);
}
