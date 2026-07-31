package com.gonet.primary.complaint.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.common.util.XssSanitizer;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.complaint.dto.ComplaintMaster;
import com.gonet.primary.complaint.dto.ComplaintMasterSaveForm;
import com.gonet.primary.complaint.dto.ComplaintMasterSearch;
import com.gonet.primary.complaint.mapper.ComplaintMasterMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true,
    transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class ComplaintMasterServiceImpl extends EgovAbstractServiceImpl
        implements ComplaintMasterService {

    private final ComplaintMasterMapper mapper;
    private final AuditLogger           auditLogger;
    private final XssSanitizer          xss;

    public ComplaintMasterServiceImpl(ComplaintMasterMapper mapper,
                                      AuditLogger auditLogger,
                                      XssSanitizer xss) {
        this.mapper      = mapper;
        this.auditLogger = auditLogger;
        this.xss         = xss;
    }

    @Override
    public PageResponse<ComplaintMaster> list(ComplaintMasterSearch search) {
        List<ComplaintMaster> rows  = mapper.list(search);
        int                   total = mapper.count(search);
        return PageResponse.of(rows, search.getPage(), search.getPageSize(), total);
    }

    @Override
    public ComplaintMaster get(String complaintMasterId) {
        return mapper.findById(complaintMasterId);
    }

    @Override
    public ComplaintMaster getBySiteCodeAndMasterCode(String siteCode, String masterCode) {
        return mapper.findBySiteCodeAndMasterCode(siteCode, masterCode);
    }

    @Override
    public ComplaintMaster getByMasterCode(String masterCode) {
        return mapper.findByMasterCode(masterCode);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(ComplaintMasterSaveForm form) {
        if (mapper.existsByCode(form.getSiteId(), form.getMasterCode(), null)) {
            throw new IllegalArgumentException("이미 사용 중인 코드입니다: " + form.getMasterCode());
        }
        ComplaintMaster master = toEntity(form);
        master.setComplaintMasterId(UuidV7Generator.generate());
        mapper.insert(master);
        auditLogger.write(AuditEvent.of("COMPLAINT_MASTER_CREATE", "tb_complaint_master")
            .withTarget(master.getComplaintMasterId())
            .withAfter("{\"masterCode\":\"" + master.getMasterCode() + "\"}"));
        return master.getComplaintMasterId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(String complaintMasterId, ComplaintMasterSaveForm form) {
        ComplaintMaster existing = mapper.findById(complaintMasterId);
        if (existing == null) throw new ResourceNotFoundException("민원 마스터를 찾을 수 없습니다.");
        ComplaintMaster master = toEntity(form);
        master.setComplaintMasterId(complaintMasterId);
        mapper.update(master);
        auditLogger.write(AuditEvent.of("COMPLAINT_MASTER_UPDATE", "tb_complaint_master")
            .withTarget(complaintMasterId));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String complaintMasterId, boolean active) {
        mapper.updateUseYn(complaintMasterId, active ? "Y" : "N");
        auditLogger.write(AuditEvent.of("COMPLAINT_MASTER_USE_TOGGLE", "tb_complaint_master")
            .withTarget(complaintMasterId)
            .withAfter("{\"useYn\":\"" + (active ? "Y" : "N") + "\"}"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void delete(String complaintMasterId) {
        mapper.softDelete(complaintMasterId);
        auditLogger.write(AuditEvent.of("COMPLAINT_MASTER_DELETE", "tb_complaint_master")
            .withTarget(complaintMasterId));
    }

    private static String yn(String v) { return "Y".equals(v) ? "Y" : "N"; }

    private ComplaintMaster toEntity(ComplaintMasterSaveForm f) {
        ComplaintMaster m = new ComplaintMaster();
        m.setSiteId(f.getSiteId());
        m.setMasterCode(f.getMasterCode());
        m.setMasterName(f.getMasterName());
        m.setDescription(f.getDescription());
        m.setMenuId(f.getMenuId());
        // notice_html — XSS sanitize 후 저장
        m.setNoticeHtml(f.getNoticeHtml() != null ? xss.safe(f.getNoticeHtml()) : null);
        m.setAllowMemberYn(yn(f.getAllowMemberYn()));
        m.setAllowOauth2Yn(yn(f.getAllowOauth2Yn()));
        m.setAllowNiceYn(yn(f.getAllowNiceYn()));
        m.setCaptchaYn(yn(f.getCaptchaYn()));
        m.setShowInList(yn(f.getShowInList()));
        m.setFileYn(yn(f.getFileYn()));
        m.setFileCountMax(f.getFileCountMax());
        m.setFileSizeMax((long) f.getFileSizeMb() * 1024 * 1024);
        m.setAnswerRequiredYn(yn(f.getAnswerRequiredYn()));
        m.setAnswerDeadlineDays(f.getAnswerDeadlineDays());
        m.setUseYn(yn(f.getUseYn()));
        return m;
    }
}
