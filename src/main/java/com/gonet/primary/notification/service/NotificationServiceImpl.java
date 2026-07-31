package com.gonet.primary.notification.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.notification.dto.Notification;
import com.gonet.primary.notification.dto.NotificationSearch;
import com.gonet.primary.notification.dto.NotificationType;
import com.gonet.primary.notification.mapper.NotificationMapper;
import com.gonet.primary.notification.mapper.StaffRecipientMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 알림 서비스 구현 — eGov {@link EgovAbstractServiceImpl} 상속.
 *
 * <p>발송 실패는 RuntimeException 으로 전파하지 않고 log WARN — 호출자(게시판/설문 등)의
 * 비즈니스 트랜잭션에 영향이 없도록 한다. 단, 본인 트랜잭션 내부 INSERT 실패만 try/catch
 * 경계로 처리하고, 정상 흐름은 {@link Transactional} 그대로 가져간다.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class NotificationServiceImpl extends EgovAbstractServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationMapper          mapper;
    private final AuditLogger                 auditLogger;
    private final NotificationPrefService     prefService;
    private final NotificationEmailDispatcher emailDispatcher;
    private final StaffRecipientMapper        staffRecipientMapper;

    public NotificationServiceImpl(NotificationMapper mapper,
                                     AuditLogger auditLogger,
                                     NotificationPrefService prefService,
                                     NotificationEmailDispatcher emailDispatcher,
                                     StaffRecipientMapper staffRecipientMapper) {
        this.mapper               = mapper;
        this.auditLogger          = auditLogger;
        this.prefService          = prefService;
        this.emailDispatcher      = emailDispatcher;
        this.staffRecipientMapper = staffRecipientMapper;
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String send(String recipientUserId,
                         NotificationType type,
                         String title,
                         String body,
                         String linkUrl,
                         String relatedEntity,
                         String relatedId) {
        if (recipientUserId == null || recipientUserId.isBlank() || title == null || title.isBlank()) {
            log.info("NOTIFICATION_SEND skip — invalid recipient/title");
            return null;
        }
        NotificationType resolvedType = (type == null) ? NotificationType.SYSTEM : type;
        String notificationId = null;

        // ── A. in-app 채널 — pref 통과 시만 INSERT
        if (prefService.isInappEnabled(recipientUserId, resolvedType)) {
            try {
                Notification n = new Notification();
                n.setNotificationId(UuidV7Generator.generate());
                n.setRecipientUserId(recipientUserId);
                n.setNotificationType(resolvedType.name());
                n.setTitle(truncate(title, 200));
                n.setBody(truncate(body, 2000));
                n.setLinkUrl(truncate(linkUrl, 500));
                n.setRelatedEntity(truncate(relatedEntity, 40));
                n.setRelatedId(truncate(relatedId, 36));
                n.setReadYn("N");
                n.setDeleteYn("N");
                mapper.insert(n);
                notificationId = n.getNotificationId();

                auditLogger.write(AuditEvent.of("NOTIFICATION_SEND", "tb_notification")
                    .withTarget(notificationId)
                    .withResult("SUCCESS")
                    .withAfter("{\"recipientUserId\":" + JsonUtils.quote(recipientUserId)
                        + ",\"type\":" + JsonUtils.quote(resolvedType.name())
                        + ",\"title\":" + JsonUtils.quote(truncate(title, 200)) + "}"));
            } catch (Exception ex) {
                log.warn("NOTIFICATION_SEND_INAPP_FAIL recipient={} type={} reason={}",
                    recipientUserId, resolvedType, ex.getMessage());
            }
        } else {
            log.info("NOTIFICATION_INAPP_OFF recipient={} type={}", recipientUserId, resolvedType);
        }

        // ── B. email 채널 — pref 통과 시만 비동기 발송
        if (prefService.isEmailEnabled(recipientUserId, resolvedType)) {
            try {
                emailDispatcher.sendAsync(recipientUserId, resolvedType,
                    truncate(title, 200), truncate(body, 2000),
                    truncate(linkUrl, 500), notificationId);
            } catch (Exception ex) {
                log.warn("NOTIFICATION_SEND_EMAIL_DISPATCH_FAIL recipient={} reason={}",
                    recipientUserId, ex.getMessage());
            }
        }

        return notificationId;
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String sendInappOnly(String recipientUserId,
                                  NotificationType type,
                                  String title,
                                  String body,
                                  String linkUrl,
                                  String relatedEntity,
                                  String relatedId) {
        if (recipientUserId == null || recipientUserId.isBlank() || title == null || title.isBlank()) {
            return null;
        }
        NotificationType resolvedType = (type == null) ? NotificationType.SYSTEM : type;
        if (!prefService.isInappEnabled(recipientUserId, resolvedType)) {
            return null;
        }
        try {
            Notification n = new Notification();
            n.setNotificationId(UuidV7Generator.generate());
            n.setRecipientUserId(recipientUserId);
            n.setNotificationType(resolvedType.name());
            n.setTitle(truncate(title, 200));
            n.setBody(truncate(body, 2000));
            n.setLinkUrl(truncate(linkUrl, 500));
            n.setRelatedEntity(truncate(relatedEntity, 40));
            n.setRelatedId(truncate(relatedId, 36));
            n.setReadYn("N");
            n.setDeleteYn("N");
            mapper.insert(n);
            return n.getNotificationId();
        } catch (Exception ex) {
            log.warn("NOTIFICATION_SEND_INAPP_ONLY_FAIL recipient={} type={} reason={}",
                recipientUserId, resolvedType, ex.getMessage());
            return null;
        }
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int sendToStaff(NotificationType type,
                             String title,
                             String body,
                             String linkUrl,
                             String relatedEntity,
                             String relatedId,
                             String excludeUserId) {
        try {
            List<String> staffUserIds = staffRecipientMapper.findActiveUserIdsByRoleCode("ROLE_STAFF");
            if (staffUserIds == null || staffUserIds.isEmpty()) {
                log.info("NOTIFICATION_TO_STAFF skip — no active staff");
                return 0;
            }
            int sent = 0;
            for (String uid : staffUserIds) {
                if (excludeUserId != null && excludeUserId.equals(uid)) continue;
                if (send(uid, type, title, body, linkUrl, relatedEntity, relatedId) != null) {
                    sent++;
                }
            }
            log.info("===NOTIFICATION_TO_STAFF type={} candidates={} sent={}",
                type, staffUserIds.size(), sent);
            return sent;
        } catch (Exception ex) {
            log.warn("NOTIFICATION_TO_STAFF_FAIL type={} reason={}",
                type, ex.getMessage());
            return 0;
        }
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int sendToMany(Collection<String> recipientUserIds,
                            NotificationType type,
                            String title,
                            String body,
                            String linkUrl,
                            String relatedEntity,
                            String relatedId) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) return 0;
        int sent = 0;
        for (String rid : recipientUserIds) {
            if (send(rid, type, title, body, linkUrl, relatedEntity, relatedId) != null) {
                sent++;
            }
        }
        return sent;
    }

    @Override
    public List<Notification> search(NotificationSearch search) {
        return mapper.findList(search == null ? new NotificationSearch() : search);
    }

    @Override
    public int count(NotificationSearch search) {
        return mapper.countList(search == null ? new NotificationSearch() : search);
    }

    @Override
    public Notification get(String notificationId) {
        return mapper.findById(notificationId);
    }

    @Override
    public int unreadCount(String recipientUserId) {
        if (recipientUserId == null || recipientUserId.isBlank()) return 0;
        return mapper.countUnreadByRecipient(recipientUserId);
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int markRead(String notificationId, String recipientUserId) {
        if (notificationId == null || recipientUserId == null) return 0;
        return mapper.markRead(notificationId, recipientUserId);
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int markAllRead(String recipientUserId) {
        if (recipientUserId == null) return 0;
        int cnt = mapper.markAllReadByRecipient(recipientUserId);
        if (cnt > 0) {
            auditLogger.write(AuditEvent.of("NOTIFICATION_MARK_ALL_READ", "tb_notification")
                .withTarget(recipientUserId)
                .withResult("SUCCESS")
                .withAfter("{\"count\":" + cnt + "}"));
        }
        return cnt;
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int softDelete(String notificationId, String recipientUserId) {
        return mapper.softDelete(notificationId, recipientUserId);
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public int adminSoftDelete(String notificationId) {
        int cnt = mapper.adminSoftDelete(notificationId);
        if (cnt > 0) {
            auditLogger.write(AuditEvent.of("NOTIFICATION_ADMIN_DELETE", "tb_notification")
                .withTarget(notificationId)
                .withResult("SUCCESS"));
        }
        return cnt;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
