package com.gonet.primary.notification.mapper;

import com.gonet.primary.notification.dto.Notification;
import com.gonet.primary.notification.dto.NotificationSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

import java.util.List;

/**
 * tb_notification CRUD Mapper.
 *
 * <p>관리자 통합 list (recipientUserId=null) + 회원 인박스 (recipientUserId 본인 강제) 공유.
 */
@EgovMapper
public interface NotificationMapper {

    List<Notification> findList(@Param("search") NotificationSearch search);

    int countList(@Param("search") NotificationSearch search);

    Notification findById(@Param("notificationId") String notificationId);

    /** 미확인 카운트 — 헤더 종 뱃지용. */
    int countUnreadByRecipient(@Param("recipientUserId") String recipientUserId);

    int insert(Notification notification);

    /** 단건 read 처리. recipientUserId 일치 검증 포함 (본인 알림만 read 가능). */
    int markRead(@Param("notificationId") String notificationId,
                  @Param("recipientUserId") String recipientUserId);

    /** 본인의 모든 미확인 알림을 read 처리. */
    int markAllReadByRecipient(@Param("recipientUserId") String recipientUserId);

    int softDelete(@Param("notificationId") String notificationId,
                    @Param("recipientUserId") String recipientUserId);

    /** 관리자 강제 soft delete — recipient 검증 없음. */
    int adminSoftDelete(@Param("notificationId") String notificationId);

    /** read=Y 이고 read_at < cutoff 인 알림 일괄 soft delete. retention 스케줄러 전용. */
    int softDeleteOldRead(@Param("cutoff") LocalDateTime cutoff);

    /** delete_yn='Y' 이고 updated_at < cutoff 인 알림 물리 DELETE. retention 스케줄러 전용. */
    int purgeOldDeleted(@Param("cutoff") LocalDateTime cutoff);

    /** preview — softDeleteOldRead 가 영향받을 행 수. */
    long countOldRead(@Param("cutoff") LocalDateTime cutoff);

    /** preview — purgeOldDeleted 가 영향받을 행 수. */
    long countOldDeleted(@Param("cutoff") LocalDateTime cutoff);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 전달
    // ------------------------------------------------------------------

    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
