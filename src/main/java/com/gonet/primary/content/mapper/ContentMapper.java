package com.gonet.primary.content.mapper;

import com.gonet.primary.content.dto.Content;
import com.gonet.primary.content.dto.ContentHistory;
import com.gonet.primary.content.dto.ContentSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_content + tb_content_history CRUD Mapper.
 *
 * <p>관리자 도메인 전용 — 사용자 프런트 조회는 별도의 읽기 경로(SiteContextMapper, 향후 ContentUsrMapper)
 * 로 분리한다. 본 Mapper 는 soft-delete 미노출 + 검색/정렬/페이징 + 상태 전환 지원.
 */
@EgovMapper
public interface ContentMapper {

    List<Content> findList(@Param("search") ContentSearch search);

    int countList(@Param("search") ContentSearch search);

    Content findById(@Param("contentId") String contentId);

    /**
     * slug 중복 체크 (사이트 내 UNIQUE) — 등록 시 excludeContentId=null, 수정 시 본인 제외.
     * slug 가 null/blank 면 0 반환 (slug 선택 입력).
     */
    int existsBySlug(@Param("siteId") String siteId,
                      @Param("slug") String slug,
                      @Param("excludeContentId") String excludeContentId);

    int insert(Content content);

    int update(Content content);

    /**
     * 상태·게시 필드만 전용으로 UPDATE — 본문 필드를 건드리지 않아 버전 증가 없이 전환.
     * {@code publishedAt}/{@code unpublishAt} 은 호출 측에서 계산해 전달.
     */
    int updateStatus(@Param("contentId") String contentId,
                      @Param("status") String status,
                      @Param("publishedAt") LocalDateTime publishedAt,
                      @Param("unpublishAt") LocalDateTime unpublishAt);

    int incrementVersion(@Param("contentId") String contentId);

    int softDelete(@Param("contentId") String contentId);

    /**
     * 색인 재구축용 — soft-delete 미노출 활성 콘텐츠 전체. JOIN tb_site 로 siteCode 채움.
     * status 무관 — Service 단에서 PUBLISHED 만 색인 등록.
     */
    List<Content> findAllForReindex();

    /**
     * 디스크 HTML → DB 동기화 전용 UPDATE.
     * body / original_content / summary / body_hash 만 갱신.
     * 다른 필드 (status / publishedAt / version_no / slug 등) 는 보존. version_no 증가 안 함.
     * (감사 컬럼은 AuditInterceptor 가 자동 주입)
     *
     * @param bodyHash 디스크 HTML 원문 SHA-256 hex (변경 감지 키)
     */
    int updateBodyOriginalSummary(@Param("contentId") String contentId,
                                    @Param("body") String body,
                                    @Param("originalContent") String originalContent,
                                    @Param("summary") String summary,
                                    @Param("bodyHash") String bodyHash);

    // ------------------------------------------------------------------
    // tb_content_history
    // ------------------------------------------------------------------

    int insertHistory(ContentHistory history);

    List<ContentHistory> findHistoryByContentId(@Param("contentId") String contentId);

    ContentHistory findHistory(@Param("contentHistoryId") String contentHistoryId);

    // ------------------------------------------------------------------
    // Soft-delete retention — RetentionScheduler 가 cutoff 를 계산해 전달
    // ------------------------------------------------------------------

    /**
     * tb_content 자기 정리 — cutoff 이전 (updated_at &lt;= cutoff) 의 delete_yn='Y' 행을 hard delete.
     * 본 SQL 은 자식 history 가 먼저 정리됐다는 전제 (childOrder 정합).
     */
    int purgeSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** dry-run 카운트 — DELETE 없이 후보 행 수만. */
    int countSoftDeletedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /**
     * tb_content_history 자식 정리 — 부모 content (delete_yn='Y' AND updated_at &lt;= cutoff)
     * 의 history 행을 hard delete. tb_content_history 자체에 delete_yn 가 없으므로
     * 부모 cutoff 기반 정리. ContentRetentionTarget(30) 보다 먼저 실행되어야 FK 위반 회피.
     */
    int purgeHistoryOfSoftDeletedContent(@Param("cutoff") LocalDateTime cutoff);

    /** dry-run 카운트 — 부모 content cutoff 기반 자식 history 행 수. */
    int countHistoryOfSoftDeletedContent(@Param("cutoff") LocalDateTime cutoff);
}
