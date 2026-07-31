package com.gonet.primary.schedule.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.html.HtmlSanitizer;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.schedule.dto.ScheduleMaster;
import com.gonet.primary.schedule.dto.ScheduleMasterSaveForm;
import com.gonet.primary.schedule.dto.ScheduleMasterSearch;
import com.gonet.primary.schedule.mapper.ScheduleMasterMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class ScheduleMasterServiceImpl extends EgovAbstractServiceImpl
        implements ScheduleMasterService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleMasterServiceImpl.class);

    private final ScheduleMasterMapper mapper;
    private final AuditLogger          auditLogger;
    private final HtmlSanitizer        htmlSanitizer;

    public ScheduleMasterServiceImpl(ScheduleMasterMapper mapper,
                                      AuditLogger auditLogger,
                                      HtmlSanitizer htmlSanitizer) {
        this.mapper        = mapper;
        this.auditLogger   = auditLogger;
        this.htmlSanitizer = htmlSanitizer;
    }

    @Override
    public List<ScheduleMaster> search(ScheduleMasterSearch search) {
        return mapper.findList(search == null ? new ScheduleMasterSearch() : search);
    }

    @Override
    public int count(ScheduleMasterSearch search) {
        return mapper.countList(search == null ? new ScheduleMasterSearch() : search);
    }

    @Override
    public ScheduleMaster get(String scheduleMasterId) {
        if (scheduleMasterId == null || scheduleMasterId.isBlank()) return null;
        return mapper.findById(scheduleMasterId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(ScheduleMasterSaveForm form) {
        Objects.requireNonNull(form, "form");
        validate(form);

        ScheduleMaster m = new ScheduleMaster();
        m.setScheduleMasterId(UuidV7Generator.generate("SCM"));
        m.setSiteId(form.getSiteId());
        m.setMenuId(blankToNull(form.getMenuId()));
        m.setMasterTitle(form.getMasterTitle().trim());
        m.setMasterContent(htmlSanitizer.sanitizeContent(form.getMasterContent()));
        m.setUseYn(ScheduleMasterSaveForm.yn(form.getUseYn()));
        m.setDeleteYn("N");
        mapper.insert(m);

        auditLogger.write(AuditEvent.of("SCHEDULE_MASTER_CREATE", "tb_schedule_master")
            .withTarget(m.getScheduleMasterId())
            .withAfter("{\"siteId\":" + JsonUtils.quote(m.getSiteId())
                + ",\"menuId\":" + JsonUtils.quote(m.getMenuId())
                + ",\"title\":" + JsonUtils.quote(m.getMasterTitle()) + "}")
            .withResult("SUCCESS"));

        log.info("===SCHEDULE_MASTER_CREATE_OK id={} site={} menu={} title={}",
            m.getScheduleMasterId(), m.getSiteId(), m.getMenuId(), m.getMasterTitle());
        return m.getScheduleMasterId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(ScheduleMasterSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getScheduleMasterId() == null || form.getScheduleMasterId().isBlank()) {
            throw new IllegalArgumentException("scheduleMasterId required");
        }
        validate(form);
        ScheduleMaster existing = mapper.findById(form.getScheduleMasterId());
        if (existing == null) {
            throw new IllegalArgumentException("일정 마스터를 찾을 수 없습니다.");
        }
        existing.setSiteId(form.getSiteId());
        existing.setMenuId(blankToNull(form.getMenuId()));
        existing.setMasterTitle(form.getMasterTitle().trim());
        existing.setMasterContent(htmlSanitizer.sanitizeContent(form.getMasterContent()));
        existing.setUseYn(ScheduleMasterSaveForm.yn(form.getUseYn()));
        mapper.update(existing);

        auditLogger.write(AuditEvent.of("SCHEDULE_MASTER_UPDATE", "tb_schedule_master")
            .withTarget(existing.getScheduleMasterId())
            .withAfter("{\"siteId\":" + JsonUtils.quote(existing.getSiteId())
                + ",\"menuId\":" + JsonUtils.quote(existing.getMenuId())
                + ",\"title\":" + JsonUtils.quote(existing.getMasterTitle())
                + ",\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String scheduleMasterId, boolean active) {
        ScheduleMaster existing = mapper.findById(scheduleMasterId);
        if (existing == null) {
            throw new IllegalArgumentException("일정 마스터를 찾을 수 없습니다.");
        }
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) return;
        mapper.updateUseYn(scheduleMasterId, next);

        auditLogger.write(AuditEvent.of("SCHEDULE_MASTER_USE_TOGGLE", "tb_schedule_master")
            .withTarget(scheduleMasterId)
            .withBefore("{\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String scheduleMasterId) {
        if (scheduleMasterId == null || scheduleMasterId.isBlank()) {
            throw new IllegalArgumentException("scheduleMasterId required");
        }
        ScheduleMaster existing = mapper.findById(scheduleMasterId);
        if (existing == null) {
            throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않습니다.");
        }
        int affected = mapper.softDelete(scheduleMasterId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + scheduleMasterId);
        }
        auditLogger.write(AuditEvent.of("SCHEDULE_MASTER_DELETE", "tb_schedule_master")
            .withTarget(scheduleMasterId)
            .withBefore("{\"title\":" + JsonUtils.quote(existing.getMasterTitle()) + "}")
            .withResult("SUCCESS"));
    }

    private static void validate(ScheduleMasterSaveForm form) {
        if (form.getSiteId() == null || form.getSiteId().isBlank()) {
            throw new IllegalArgumentException("사이트를 선택해 주세요.");
        }
        if (form.getMasterTitle() == null || form.getMasterTitle().isBlank()) {
            throw new IllegalArgumentException("그룹 제목을 입력해 주세요.");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
