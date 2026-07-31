package com.gonet.primary.complaint.service;

import com.gonet.primary.complaint.dto.ComplaintAnswer;
import com.gonet.primary.complaint.dto.ComplaintAnswerSaveForm;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ComplaintAnswerService {

    List<ComplaintAnswer> listByArticle(String articleId);

    ComplaintAnswer get(String answerId);

    String create(ComplaintAnswerSaveForm form, Authentication auth);

    void update(String answerId, ComplaintAnswerSaveForm form);

    void delete(String answerId);
}
