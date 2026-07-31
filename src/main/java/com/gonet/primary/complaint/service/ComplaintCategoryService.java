package com.gonet.primary.complaint.service;

import com.gonet.primary.complaint.dto.ComplaintCategory;
import com.gonet.primary.complaint.dto.ComplaintCategorySaveForm;

import java.util.List;

public interface ComplaintCategoryService {

    List<ComplaintCategory> listByMaster(String complaintMasterId);

    ComplaintCategory get(String categoryId);

    String create(ComplaintCategorySaveForm form);

    void update(String categoryId, ComplaintCategorySaveForm form);

    void toggleUse(String categoryId, boolean active);

    void delete(String categoryId);
}
