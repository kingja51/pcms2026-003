package com.gonet.primary.complaint.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.complaint.dto.ComplaintCategory;
import com.gonet.primary.complaint.dto.ComplaintCategorySaveForm;
import com.gonet.primary.complaint.mapper.ComplaintCategoryMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true,
    transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class ComplaintCategoryServiceImpl extends EgovAbstractServiceImpl
        implements ComplaintCategoryService {

    private final ComplaintCategoryMapper mapper;
    private final AuditLogger             auditLogger;

    public ComplaintCategoryServiceImpl(ComplaintCategoryMapper mapper,
                                        AuditLogger auditLogger) {
        this.mapper      = mapper;
        this.auditLogger = auditLogger;
    }

    @Override
    public List<ComplaintCategory> listByMaster(String complaintMasterId) {
        return mapper.findByMasterId(complaintMasterId);
    }

    @Override
    public ComplaintCategory get(String categoryId) {
        return mapper.findById(categoryId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(ComplaintCategorySaveForm form) {
        if (mapper.existsByCode(form.getComplaintMasterId(), form.getCategoryCode(), null)) {
            throw new IllegalArgumentException("이미 사용 중인 카테고리 코드입니다: " + form.getCategoryCode());
        }
        ComplaintCategory cat = new ComplaintCategory();
        cat.setCategoryId(UuidV7Generator.generate());
        cat.setComplaintMasterId(form.getComplaintMasterId());
        cat.setCategoryCode(form.getCategoryCode());
        cat.setCategoryName(form.getCategoryName());
        cat.setDescription(form.getDescription());
        cat.setSortOrder(form.getSortOrder());
        cat.setUseYn(form.getUseYn());
        mapper.insert(cat);
        auditLogger.write(AuditEvent.of("COMPLAINT_CATEGORY_CREATE", "tb_complaint_category")
            .withTarget(cat.getCategoryId())
            .withAfter("{\"categoryCode\":\"" + cat.getCategoryCode() + "\"}"));
        return cat.getCategoryId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(String categoryId, ComplaintCategorySaveForm form) {
        ComplaintCategory cat = mapper.findById(categoryId);
        if (cat == null) throw new ResourceNotFoundException("카테고리를 찾을 수 없습니다.");
        cat.setCategoryName(form.getCategoryName());
        cat.setDescription(form.getDescription());
        cat.setSortOrder(form.getSortOrder());
        mapper.update(cat);
        auditLogger.write(AuditEvent.of("COMPLAINT_CATEGORY_UPDATE", "tb_complaint_category")
            .withTarget(categoryId));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String categoryId, boolean active) {
        mapper.updateUseYn(categoryId, active ? "Y" : "N");
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void delete(String categoryId) {
        mapper.softDelete(categoryId);
        auditLogger.write(AuditEvent.of("COMPLAINT_CATEGORY_DELETE", "tb_complaint_category")
            .withTarget(categoryId));
    }
}
