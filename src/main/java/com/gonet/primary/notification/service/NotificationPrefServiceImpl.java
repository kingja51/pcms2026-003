package com.gonet.primary.notification.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.notification.dto.NotificationPref;
import com.gonet.primary.notification.dto.NotificationPrefForm;
import com.gonet.primary.notification.dto.NotificationType;
import com.gonet.primary.notification.mapper.NotificationPrefMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class NotificationPrefServiceImpl extends EgovAbstractServiceImpl implements NotificationPrefService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPrefServiceImpl.class);

    /** 미등록 사용자 기본값. 필요 시 application.yml 외부화. */
    private static final boolean DEFAULT_INAPP = true;
    private static final boolean DEFAULT_EMAIL = true;

    private final NotificationPrefMapper mapper;
    private final AuditLogger            auditLogger;

    public NotificationPrefServiceImpl(NotificationPrefMapper mapper, AuditLogger auditLogger) {
        this.mapper      = mapper;
        this.auditLogger = auditLogger;
    }

    @Override
    public List<NotificationPref> findByUser(String userId) {
        if (userId == null || userId.isBlank()) return List.of();
        return mapper.findByUser(userId);
    }

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void save(NotificationPrefForm form) {
        if (form == null) throw new IllegalArgumentException("form null");
        if (form.getUserId() == null || form.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId null");
        }
        String type = (form.getNotificationType() == null || form.getNotificationType().isBlank())
            ? "ALL" : form.getNotificationType().trim();

        NotificationPref p = new NotificationPref();
        p.setPrefId(UuidV7Generator.generate("PRF"));
        p.setUserId(form.getUserId().trim());
        p.setNotificationType(type);
        p.setChannelInappYn(normalizeYn(form.getChannelInappYn()));
        p.setChannelEmailYn(normalizeYn(form.getChannelEmailYn()));
        mapper.upsert(p);

        auditLogger.write(AuditEvent.of("NOTIFICATION_PREF_SAVE", "tb_notification_pref")
            .withTarget(p.getUserId())
            .withResult("SUCCESS")
            .withAfter("{\"userId\":" + JsonUtils.quote(p.getUserId())
                + ",\"type\":" + JsonUtils.quote(type)
                + ",\"inapp\":" + JsonUtils.quote(p.getChannelInappYn())
                + ",\"email\":" + JsonUtils.quote(p.getChannelEmailYn()) + "}"));
    }

    @Override
    public boolean isInappEnabled(String userId, NotificationType type) {
        return resolve(userId, type, true);
    }

    @Override
    public boolean isEmailEnabled(String userId, NotificationType type) {
        return resolve(userId, type, false);
    }

    /**
     * resolve — 타입별 row → ALL row → hard-coded default. row 가 있으면 'Y'/'N' 평가,
     * 없으면 다음 fallback. 미존재 사용자는 default 두 채널 모두 ON.
     *
     * @param inapp true 면 in-app 채널, false 면 email 채널
     */
    private boolean resolve(String userId, NotificationType type, boolean inapp) {
        if (userId == null || userId.isBlank()) {
            return inapp ? DEFAULT_INAPP : DEFAULT_EMAIL;
        }
        try {
            String typeName = (type == null) ? null : type.name();
            if (typeName != null) {
                NotificationPref p = mapper.findOne(userId, typeName);
                if (p != null) return inapp ? p.isInappEnabled() : p.isEmailEnabled();
            }
            NotificationPref all = mapper.findOne(userId, "ALL");
            if (all != null) return inapp ? all.isInappEnabled() : all.isEmailEnabled();
        } catch (Exception ex) {
            log.warn("NOTIFICATION_PREF_RESOLVE_FAIL user={} type={} reason={}",
                userId, type, ex.getMessage());
        }
        return inapp ? DEFAULT_INAPP : DEFAULT_EMAIL;
    }

    private static String normalizeYn(String raw) {
        return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
    }
}
