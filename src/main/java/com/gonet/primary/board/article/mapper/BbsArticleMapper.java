package com.gonet.primary.board.article.mapper;

import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.dto.BbsArticleSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_bbs_article CRUD Mapper.
 *
 * <p>설계 결정:
 * <ul>
 *   <li>JOIN tb_bbs_master 로 bbsCode/bbsName/siteId/siteCode/commentYn/fileYn 동시 조회 —
 *       Service 가 게시판 정책에 즉시 접근 가능 (write_auth/read_auth/comment_yn 등)</li>
 *   <li>목록 정렬: 공지(notice_yn='Y') 우선, 그 다음 최신순 (created_at DESC)</li>
 *   <li>상태 필터: 사용자 = PUBLISHED 만 강제. 관리자 = 검색 조건의 status 그대로</li>
 *   <li>view_count 증가는 별도 UPDATE — 도메인 인터셉터의 updated_by 갱신 회피</li>
 * </ul>
 */
@EgovMapper
public interface BbsArticleMapper {

    List<BbsArticle> findList(@Param("search") BbsArticleSearch search);

    int countList(@Param("search") BbsArticleSearch search);

    BbsArticle findById(@Param("articleId") String articleId);

    /**
     * 게시글 작성자 식별자({@code created_by}) 만 조회 — OWNER_PRIVACY 다운로드 권한
     * 평가용 lightweight lookup. soft-delete 된 글도 조회 (감사 흐름 + 본인이라면 본인 글
     * 첨부는 받을 수 있어야 하므로 delete_yn 무시). 미존재 시 null.
     */
    String findCreatedBy(@Param("articleId") String articleId);

    /**
     * 게시판 + 게시글 ID 모두 일치 (URL 변조 방지). 사용자 화면에서 사용.
     */
    BbsArticle findByBbsAndId(@Param("bbsMasterId") String bbsMasterId,
                                @Param("articleId") String articleId);

    /**
     * 이전글 — 같은 게시판에서 현재 글보다 먼저 작성된(더 과거) PUBLISHED 글 1건.
     * created_at 동률은 article_id(UUID v7, 시간순)로 타이브레이크. 없으면 null.
     * 상세 하단 이전글/다음글 내비(fragments/bbs-prev-next) 용 — article_id/title/created_at 만 채워짐.
     */
    BbsArticle findPrev(@Param("bbsMasterId") String bbsMasterId,
                        @Param("createdAt") LocalDateTime createdAt,
                        @Param("articleId") String articleId);

    /** 다음글 — 현재 글보다 나중에 작성된(더 최신) PUBLISHED 글 1건. {@link #findPrev} 참조. */
    BbsArticle findNext(@Param("bbsMasterId") String bbsMasterId,
                        @Param("createdAt") LocalDateTime createdAt,
                        @Param("articleId") String articleId);

    int insert(BbsArticle article);

    int update(BbsArticle article);

    /** soft delete + status='DELETED' */
    int softDelete(@Param("articleId") String articleId);

    /** 관리자 강제 상태 전환 (HIDDEN/REPORTED/PUBLISHED 토글). */
    int updateStatus(@Param("articleId") String articleId,
                      @Param("status") String status);

    /** 공지 토글 — 관리자만 호출. */
    int updateNoticeYn(@Param("articleId") String articleId,
                        @Param("noticeYn") String noticeYn);

    /**
     * view_count 증가. 6감사컬럼은 건드리지 않음 (updated_by 보존).
     * 호출 측이 cookie 등으로 중복 방지 후 호출.
     */
    int incrementViewCount(@Param("articleId") String articleId);

    /**
     * comment_count 재계산 — PUBLISHED 댓글 수로 갱신.
     * 댓글 CUD/상태 전환 후 호출. 6감사컬럼 미주입 (updated_by 보존).
     */
    int recomputeCommentCount(@Param("articleId") String articleId);

    /**
     * like_count 절대값 설정 — 명시적 reset(0) / 전체 재계산 시에만 사용.
     * 일반 토글 경로는 {@link #incrementLikeCount} 를 사용해 race 회피.
     */
    int updateLikeCount(@Param("articleId") String articleId,
                         @Param("likeCount") long likeCount);

    /**
     * report_count 절대값 설정 — 명시적 reset(0) / 전체 재계산 시에만 사용.
     * 일반 신고 경로는 {@link #incrementReportCount} 를 사용해 race 회피.
     */
    int updateReportCount(@Param("articleId") String articleId,
                           @Param("reportCount") long reportCount);

    /**
     * like_count 원자적 증감 — 좋아요 토글의 표준 경로 (H-1 race 가드).
     * delta: +1 (ON) / -1 (OFF). 단일 UPDATE 로 처리. underflow 클램프 적용.
     */
    int incrementLikeCount(@Param("articleId") String articleId,
                            @Param("delta") int delta);

    /**
     * report_count 원자적 증감 — 신고 적재 표준 경로 (H-1 race 가드).
     */
    int incrementReportCount(@Param("articleId") String articleId,
                              @Param("delta") int delta);

    /**
     * 신고 누적 임계 도달 시 PUBLISHED → REPORTED 자동 전환.
     * 이미 REPORTED/HIDDEN/DELETED 상태면 변경하지 않음.
     */
    int markStatusReportedIfPublished(@Param("articleId") String articleId);

    /**
     * 색인 재구축용 — soft-delete 미노출 활성 게시글 전체. JOIN tb_bbs_master.
     * status 무관 — Service 단에서 PUBLISHED 만 색인 등록, 그 외는 비활성 처리.
     */
    List<BbsArticle> findAllForReindex();

    /**
     * 회원 대시보드 — 본인이 작성한 최신 게시글.
     * created_by = userId AND delete_yn='N'. PUBLISHED/HIDDEN/REPORTED 모두 포함하여
     * 본인은 자신의 모든 글을 볼 수 있게 한다 (DELETED 만 제외).
     */
    List<BbsArticle> findRecentByCreatedBy(@Param("createdBy") String createdBy,
                                            @Param("limit") int limit);

    /**
     * Soft-delete retention — cutoff 이전 (updated_at &lt;= cutoff) 의
     * delete_yn='Y' 행을 hard delete. 호출 측에서 도메인별 트랜잭션을 연다.
     */
    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** Soft-delete retention — dry-run 카운트. DELETE 없이 후보 행 수만. */
    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
