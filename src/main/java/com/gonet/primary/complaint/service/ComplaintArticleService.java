package com.gonet.primary.complaint.service;

import com.gonet.common.dto.PageResponse;
import com.gonet.primary.complaint.dto.ComplaintArticle;
import com.gonet.primary.complaint.dto.ComplaintArticleSaveForm;
import com.gonet.primary.complaint.dto.ComplaintArticleSearch;
import com.gonet.primary.complaint.dto.ComplaintMaster;
import com.gonet.primary.file.dto.FileItem;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ComplaintArticleService {

    PageResponse<ComplaintArticle> list(ComplaintArticleSearch search);

    /** 사용자 목록 — show_in_list=Y 시 순번/제목/등록일만 */
    PageResponse<ComplaintArticle> listForUser(ComplaintArticleSearch search);

    ComplaintArticle get(String articleId);

    ComplaintArticle getByComplaintNo(String complaintMasterId, String complaintNo);

    /**
     * 본인 여부 확인 — 열람 허용 조건 판별.
     * STAFF 이상이면 항상 true.
     */
    boolean isOwnerOrStaff(ComplaintArticle article, Authentication auth);

    String submit(ComplaintMaster master, ComplaintArticleSaveForm form, Authentication auth);

    void update(ComplaintMaster master, String articleId, ComplaintArticleSaveForm form, Authentication auth);

    void updateStatus(String articleId, String status);

    void close(String articleId, Authentication auth);

    void delete(String articleId);

    List<FileItem> listAttachments(String fileGroupId);
}
