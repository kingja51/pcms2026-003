package com.gonet.primary.notification.mapper;

import com.gonet.primary.notification.dto.NotificationPref;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@EgovMapper
public interface NotificationPrefMapper {

    /** 사용자의 모든 pref 행 — 마이페이지 표 노출용. */
    List<NotificationPref> findByUser(@Param("userId") String userId);

    /** 단일 (user, type) 조회. */
    NotificationPref findOne(@Param("userId") String userId,
                              @Param("notificationType") String notificationType);

    int insert(NotificationPref pref);

    int update(NotificationPref pref);

    /**
     * upsert 헬퍼 — 기존 row 있으면 update, 없으면 insert. Service 단에서 (find → branch)
     * 패턴 대신 한 번에 처리하기 위함. INSERT ... ON DUPLICATE KEY UPDATE.
     */
    int upsert(NotificationPref pref);
}
