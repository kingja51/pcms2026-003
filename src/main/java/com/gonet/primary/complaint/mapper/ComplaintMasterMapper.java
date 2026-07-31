package com.gonet.primary.complaint.mapper;

import com.gonet.primary.complaint.dto.ComplaintMaster;
import com.gonet.primary.complaint.dto.ComplaintMasterSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

import java.util.List;

@EgovMapper
public interface ComplaintMasterMapper {

    List<ComplaintMaster> list(ComplaintMasterSearch search);

    int count(ComplaintMasterSearch search);

    ComplaintMaster findById(String complaintMasterId);

    ComplaintMaster findBySiteCodeAndMasterCode(String siteCode, String masterCode);

    ComplaintMaster findByMasterCode(String masterCode);

    boolean existsByCode(String siteId, String masterCode, String excludeId);

    void insert(ComplaintMaster master);

    void update(ComplaintMaster master);

    void updateUseYn(String complaintMasterId, String useYn);

    void softDelete(String complaintMasterId);
}
