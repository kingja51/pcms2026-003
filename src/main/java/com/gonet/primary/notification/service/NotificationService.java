package com.gonet.primary.notification.service;

import com.gonet.primary.notification.dto.Notification;
import com.gonet.primary.notification.dto.NotificationSearch;
import com.gonet.primary.notification.dto.NotificationType;

import java.util.Collection;
import java.util.List;

/**
 * 알림 서비스 — 발송 + 인박스 조회 + read 토글.
 *
 * <p>발송 진입점은 {@link #send(String, NotificationType, String, String, String, String, String)}
 * 단일 — 트리거 도메인(BoardReport/BoardComment/SurveyResponse 등) 은 모두 이 메서드 경유.
 *
 * <p>실패는 RuntimeException 으로 전파하지 않고 log only — 비즈니스 트랜잭션에 영향 주지 않는다.
 */
public interface NotificationService {

    /**
     * 알림 1건 발송.
     *
     * @param recipientUserId 수신자 user_seq (회원/직원/관리자 공통)
     * @param type            알림 타입
     * @param title           제목 (200자 이내)
     * @param body            본문 (옵션, 2000자 이내)
     * @param linkUrl         클릭 시 이동 (옵션)
     * @param relatedEntity   관련 엔티티 타입 (옵션)
     * @param relatedId       관련 엔티티 PK (옵션)
     * @return 발송된 notification_id (실패 시 null)
     */
    String send(String recipientUserId,
                  NotificationType type,
                  String title,
                  String body,
                  String linkUrl,
                  String relatedEntity,
                  String relatedId);

    /**
     * in-app 인박스만 적재 — 이메일 채널 skip. 이미 별도 email 흐름(예: 휴면 안내 mail
     * 템플릿) 이 있는 트리거에서 중복 발송 회피용. pref.inapp 가 OFF 면 그대로 skip.
     */
    String sendInappOnly(String recipientUserId,
                           NotificationType type,
                           String title,
                           String body,
                           String linkUrl,
                           String relatedEntity,
                           String relatedId);

    /**
     * STAFF 이상 활성 직원/관리자 모두에게 broadcast — 운영 알림용.
     *
     * <p>{@code excludeUserId} 는 발신자 본인 자동 제외 (자기 자신 알림 회피). null 이면
     * 모든 STAFF 에게 발송. 0 명일 수 있음 — 예: STAFF 권한자 미존재. 호출자 트랜잭션 영향 없음.
     *
     * @return 실제 발송된 알림 건수
     */
    int sendToStaff(NotificationType type,
                      String title,
                      String body,
                      String linkUrl,
                      String relatedEntity,
                      String relatedId,
                      String excludeUserId);

    /** 다건 수신자 동시 발송 — 같은 내용. self 알림 회피는 호출자 책임. */
    int sendToMany(Collection<String> recipientUserIds,
                     NotificationType type,
                     String title,
                     String body,
                     String linkUrl,
                     String relatedEntity,
                     String relatedId);

    /** 검색·페이징 — 회원 인박스(recipientUserId 강제) 또는 관리자 통합. */
    List<Notification> search(NotificationSearch search);

    int count(NotificationSearch search);

    Notification get(String notificationId);

    int unreadCount(String recipientUserId);

    /**
     * 본인 알림 read 처리 — 다른 사용자의 알림은 무시 (UPDATE 0 row).
     * 클릭 추적용으로 호출되므로 멱등.
     */
    int markRead(String notificationId, String recipientUserId);

    int markAllRead(String recipientUserId);

    int softDelete(String notificationId, String recipientUserId);

    /** 관리자 강제 삭제 — recipient 검증 없음. */
    int adminSoftDelete(String notificationId);
}
