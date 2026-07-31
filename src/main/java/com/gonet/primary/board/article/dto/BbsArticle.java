package com.gonet.primary.board.article.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_bbs_article 엔티티 — 게시글.
 *
 * <p>관계:
 * <ul>
 *   <li>{@code bbs_master_id} → tb_bbs_master (FK) — 어느 게시판 소속</li>
 *   <li>{@code writer_user_id} → tb_member/employee/admin (논리 FK) — 비로그인 GUEST 면 NULL</li>
 * </ul>
 *
 * <p>표시용 JOIN 필드 (비-엔티티):
 * <ul>
 *   <li>{@code bbsCode} / {@code bbsName} — 게시판 식별</li>
 *   <li>{@code siteId} / {@code siteCode} — 사이트 식별</li>
 *   <li>{@code commentYn} / {@code fileYn} — 게시판 정책 노출</li>
 * </ul>
 */
@Getter
@Setter
public class BbsArticle extends BaseEntity implements SoftDeletable {

    private String articleId;
    private String bbsMasterId;
    private String categoryId;
    private String fileGroupId;

    private String writerUserId;
    private String writerUserType;
    private String writerName;
    private String writerPassword;

    private String  title;
    private String  content;

    /** BODO 게시판 — 외부 인용 언론사명. 다른 도메인은 null. */
    private String  pressName;

    /** BODO 게시판은 외부 기사 URL, YOUTUBE 게시판은 영상 URL. 다른 도메인은 null. */
    private String  linkUrl;

    /** 게시 일자 (사용자 표기용). NULL 이면 created_at 의 일자 부분으로 폴백. */
    private java.time.LocalDate publishedAt;

    private String  noticeYn;
    private String  secretYn;
    private long    viewCount;
    private long    likeCount;
    private long    reportCount;
    private int     commentCount;
    private String  clientIp;
    private String  status;
    private String  deleteYn;

    private String bbsCode;
    private String bbsName;
    private String bbsType;
    private String siteId;
    private String siteCode;
    private String commentYn;
    private String fileYn;
    private String categoryCode;
    private String categoryName;

    /**
     * 게시글 file_group 안의 첫 이미지 file_id — 카드뉴스/갤러리형 list 의 cover 썸네일.
     * mapper 의 LEFT JOIN 서브쿼리로 채워짐. 이미지 첨부가 없으면 null.
     */
    private String coverFileId;

    public BbsArticleStatus statusEnum() {
        return BbsArticleStatus.safeParse(status);
    }

    /** 비밀글 여부. */
    public boolean isSecret() {
        return "Y".equalsIgnoreCase(secretYn);
    }

    /** 공지 글 여부 (목록 상단 고정). */
    public boolean isNotice() {
        return "Y".equalsIgnoreCase(noticeYn);
    }

    /**
     * YOUTUBE 게시판용 — {@link #linkUrl} 에서 video ID 11자리 추출.
     * 지원 형태: youtu.be/ID, youtube.com/watch?v=ID, youtube.com/embed/ID, youtube.com/shorts/ID.
     * 추출 실패 시 null. 템플릿에서 ${article.youtubeId} 로 접근.
     */
    public String getYoutubeId() {
        return extractYoutubeId(linkUrl);
    }

    static String extractYoutubeId(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        java.util.regex.Matcher m = YOUTUBE_ID_PATTERN.matcher(u);
        return m.find() ? m.group(1) : null;
    }

    private static final java.util.regex.Pattern YOUTUBE_ID_PATTERN =
        java.util.regex.Pattern.compile(
            "(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|v/))([A-Za-z0-9_-]{11})");
}
