package com.gonet.primary.survey.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.html.HtmlSanitizer;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.survey.dto.SurveyMaster;
import com.gonet.primary.survey.dto.SurveyMasterSaveForm;
import com.gonet.primary.survey.dto.SurveyMasterSearch;
import com.gonet.primary.survey.mapper.SurveyMasterMapper;
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
public class SurveyMasterServiceImpl extends EgovAbstractServiceImpl
        implements SurveyMasterService {

    private static final Logger log = LoggerFactory.getLogger(SurveyMasterServiceImpl.class);

    private final SurveyMasterMapper mapper;
    private final AuditLogger        auditLogger;
    private final HtmlSanitizer      htmlSanitizer;

    public SurveyMasterServiceImpl(SurveyMasterMapper mapper,
                                    AuditLogger auditLogger,
                                    HtmlSanitizer htmlSanitizer) {
        this.mapper        = mapper;
        this.auditLogger   = auditLogger;
        this.htmlSanitizer = htmlSanitizer;
    }

    @Override
    public List<SurveyMaster> search(SurveyMasterSearch search) {
        return mapper.findList(search == null ? new SurveyMasterSearch() : search);
    }

    @Override
    public int count(SurveyMasterSearch search) {
        return mapper.countList(search == null ? new SurveyMasterSearch() : search);
    }

    @Override
    public SurveyMaster get(String surveyMasterId) {
        if (surveyMasterId == null || surveyMasterId.isBlank()) return null;
        return mapper.findById(surveyMasterId);
    }


    @Override
    public SurveyMaster findBySiteCode(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) return null;
        return mapper.findBySiteCode(siteCode);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(SurveyMasterSaveForm form) {
        Objects.requireNonNull(form, "form");
        validate(form);

        SurveyMaster m = new SurveyMaster();
        m.setSurveyMasterId(UuidV7Generator.generate("SVM"));
        m.setSiteId(form.getSiteId());
        m.setMenuId(blankToNull(form.getMenuId()));
        m.setMasterTitle(form.getMasterTitle().trim());
        m.setMasterContent(htmlSanitizer.sanitizeContent(form.getMasterContent()));
        m.setUseYn(SurveyMasterSaveForm.yn(form.getUseYn()));
        m.setDeleteYn("N");
        mapper.insert(m);

        auditLogger.write(AuditEvent.of("SURVEY_MASTER_CREATE", "tb_survey_master")
            .withTarget(m.getSurveyMasterId())
            .withAfter("{\"siteId\":" + JsonUtils.quote(m.getSiteId())
                + ",\"menuId\":" + JsonUtils.quote(m.getMenuId())
                + ",\"title\":" + JsonUtils.quote(m.getMasterTitle()) + "}")
            .withResult("SUCCESS"));

        log.info("===SURVEY_MASTER_CREATE_OK id={} site={} menu={} title={}",
            m.getSurveyMasterId(), m.getSiteId(), m.getMenuId(), m.getMasterTitle());
        return m.getSurveyMasterId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(SurveyMasterSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getSurveyMasterId() == null || form.getSurveyMasterId().isBlank()) {
            throw new IllegalArgumentException("surveyMasterId required");
        }
        validate(form);
        SurveyMaster existing = mapper.findById(form.getSurveyMasterId());
        if (existing == null) {
            throw new ResourceNotFoundException("설문 마스터를 찾을 수 없습니다.");
        }
        existing.setSiteId(form.getSiteId());
        existing.setMenuId(blankToNull(form.getMenuId()));
        existing.setMasterTitle(form.getMasterTitle().trim());
        existing.setMasterContent(htmlSanitizer.sanitizeContent(form.getMasterContent()));
        existing.setUseYn(SurveyMasterSaveForm.yn(form.getUseYn()));
        mapper.update(existing);

        auditLogger.write(AuditEvent.of("SURVEY_MASTER_UPDATE", "tb_survey_master")
            .withTarget(existing.getSurveyMasterId())
            .withAfter("{\"siteId\":" + JsonUtils.quote(existing.getSiteId())
                + ",\"menuId\":" + JsonUtils.quote(existing.getMenuId())
                + ",\"title\":" + JsonUtils.quote(existing.getMasterTitle())
                + ",\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String surveyMasterId, boolean active) {
        SurveyMaster existing = mapper.findById(surveyMasterId);
        if (existing == null) {
            throw new ResourceNotFoundException("설문 마스터를 찾을 수 없습니다.");
        }
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) return;
        mapper.updateUseYn(surveyMasterId, next);

        auditLogger.write(AuditEvent.of("SURVEY_MASTER_USE_TOGGLE", "tb_survey_master")
            .withTarget(surveyMasterId)
            .withBefore("{\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String surveyMasterId) {
        if (surveyMasterId == null || surveyMasterId.isBlank()) {
            throw new IllegalArgumentException("surveyMasterId required");
        }
        SurveyMaster existing = mapper.findById(surveyMasterId);
        if (existing == null) {
            throw new ResourceNotFoundException("이미 삭제되었거나 존재하지 않습니다.");
        }
        int affected = mapper.softDelete(surveyMasterId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + surveyMasterId);
        }
        auditLogger.write(AuditEvent.of("SURVEY_MASTER_DELETE", "tb_survey_master")
            .withTarget(surveyMasterId)
            .withBefore("{\"title\":" + JsonUtils.quote(existing.getMasterTitle()) + "}")
            .withResult("SUCCESS"));
    }

    private static void validate(SurveyMasterSaveForm form) {
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
