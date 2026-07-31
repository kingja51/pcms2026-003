package com.gonet.primary.complaint.mapper;

import com.gonet.primary.complaint.dto.ComplaintArticle;
import com.gonet.primary.complaint.dto.ComplaintArticleSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@EgovMapper
public interface ComplaintArticleMapper {

    List<ComplaintArticle> list(ComplaintArticleSearch search);

    int count(ComplaintArticleSearch search);

    /** 사용자 목록 — show_in_list=Y 일 때 순번/제목/등록일만 반환 */
    List<ComplaintArticle> listForUser(ComplaintArticleSearch search);

    int countForUser(ComplaintArticleSearch search);

    ComplaintArticle findById(String articleId);

    ComplaintArticle findByComplaintNo(@Param("complaintMasterId") String complaintMasterId,
                                      @Param("complaintNo")      String complaintNo);

    /** 오늘 날짜 기준 당일 접수번호 채번용 카운트 */
    int countTodayByMaster(@Param("complaintMasterId") String complaintMasterId,
                           @Param("datePrefix")        String datePrefix);

    void insert(ComplaintArticle article);

    void update(ComplaintArticle article);

    void updateStatus(@Param("articleId") String articleId,
                      @Param("status")    String status);

    void updateAnsweredAt(String articleId);

    void updateClosedAt(String articleId);

    void incrementAnswerCount(String articleId);

    void softDelete(String articleId);
}
