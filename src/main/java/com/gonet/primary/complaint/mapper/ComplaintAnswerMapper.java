package com.gonet.primary.complaint.mapper;

import com.gonet.primary.complaint.dto.ComplaintAnswer;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

import java.util.List;

@EgovMapper
public interface ComplaintAnswerMapper {

    List<ComplaintAnswer> findByArticleId(String articleId);

    ComplaintAnswer findById(String answerId);

    void insert(ComplaintAnswer answer);

    void update(ComplaintAnswer answer);

    void softDelete(String answerId);
}
