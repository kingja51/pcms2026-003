package com.gonet.primary.complaint.mapper;

import com.gonet.primary.complaint.dto.ComplaintCategory;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

import java.util.List;

@EgovMapper
public interface ComplaintCategoryMapper {

    List<ComplaintCategory> findByMasterId(String complaintMasterId);

    ComplaintCategory findById(String categoryId);

    boolean existsByCode(String complaintMasterId, String categoryCode, String excludeId);

    void insert(ComplaintCategory category);

    void update(ComplaintCategory category);

    void updateUseYn(String categoryId, String useYn);

    void softDelete(String categoryId);
}
