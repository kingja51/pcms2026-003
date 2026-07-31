package com.gonet.primary.complaint.service;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.complaint.dto.ComplaintMaster;
import com.gonet.primary.complaint.dto.ComplaintMasterSaveForm;
import com.gonet.primary.complaint.dto.ComplaintMasterSearch;

public interface ComplaintMasterService {

    PageResponse<ComplaintMaster> list(ComplaintMasterSearch search);

    ComplaintMaster get(String complaintMasterId);

    ComplaintMaster getBySiteCodeAndMasterCode(String siteCode, String masterCode);

    ComplaintMaster getByMasterCode(String masterCode);

    String create(ComplaintMasterSaveForm form);

    void update(String complaintMasterId, ComplaintMasterSaveForm form);

    void toggleUse(String complaintMasterId, boolean active);

    void delete(String complaintMasterId);
}
