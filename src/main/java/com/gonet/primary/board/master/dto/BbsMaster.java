package com.gonet.primary.board.master.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_bbs_master 엔티티 — 게시판 마스터 (생성/수정/사용중지 단위).
 *
 * <p>관계:
 * <ul>
 *   <li>{@code site_id} → tb_site (FK) — 사이트별 게시판 카탈로그</li>
 *   <li>UNIQUE {@code (site_id, bbs_code)} — 사이트 내 코드 유일</li>
 * </ul>
 *
 * <p>표시용 JOIN 필드(비-엔티티): {@code siteCode}, {@code siteName}.
 */
@Getter
@Setter
public class BbsMaster extends BaseEntity implements SoftDeletable, UseFlagged {

    private String bbsMasterId;
    private String siteId;
    private String menuId;
    private String bbsCode;
    private String bbsName;
    private String bbsType;

    private String  commentYn;
    private String  fileYn;
    private int     fileCountMax;
    private long    fileSizeMax;
    private String  anonymousYn;
    private String  noticeTopYn;
    /**
     * 게시글 본문 HTML 사용 여부.
     * <ul>
     *   <li>{@code 'Y'} : article.content 를 화이트리스트 sanitizer 적용 후 {@code th:utext}
     *       로 렌더. 작성자가 서식(굵기/링크/이미지 등) 을 입력할 수 있다.</li>
     *   <li>{@code 'N'} : article.content 를 평문으로 강제. 화면은 {@code th:text} 로
     *       자동 이스케이프 — &lt;script&gt; 등 모든 태그가 텍스트로 표시됨.</li>
     * </ul>
     * Sanitizer 는 {@code Y/N} 무관하게 저장 시 강제로 적용 (DB 에 raw script 잔존 차단).
     */
    private String  htmlYn;

    /**
     * CAPTCHA 사용 여부 (Y/N). 'Y' 이면 게시판 글쓰기 시 reCAPTCHA v3 검증 강제.
     * 기본값 'N' (안전 우선 — 운영자가 명시 활성화).
     */
    private String  captchaYn;

    private String readAuth;
    private String writeAuth;
    /**
     * 첨부파일 다운로드 권한 — read_auth 와 별개로 운영.
     * 값: ANONYMOUS / ROLE_MEMBER / ROLE_EMPLOYEE / ROLE_STAFF /
     *     OWNER_PRIVACY / ROLE_MANAGER / ROLE_ADMIN.
     * 기본값(DDL): ANONYMOUS (2026-07-07 ROLE_MEMBER 에서 변경 — 공개 자료 중심 운영).
     * OWNER_PRIVACY 는 (a) tb_bbs_article.created_by == 본인,
     * (b) ROLE_PRIVACY 명시 부여자만 통과 — ROLE_ADMIN 도 자동 통과 안 함.
     */
    private String downloadAuth;

    private String useYn;
    private String description;
    /**
     * 통합 게시판(전체글 보기) 모드 — 대상 bbs_master_id 의 CSV.
     * NULL/빈문자 = 일반 게시판. 비어있지 않으면 list/count 에서 대상 게시판 글들을
     * 합쳐서 보여주는 read-only 뷰. 작성/수정/삭제는 거부됨.
     */
    private String groupedBoardIds;
    private String deleteYn;

    private String siteCode;
    private String siteName;
    private String layoutPath;

    /** 활성 게시글 수 (목록용 — 서브쿼리로 채움). */
    private long articleCount;

    public BbsType bbsTypeEnum() {
        return BbsType.safeParse(bbsType);
    }

    /** UI 표시용 — file_size_max(byte) → MB 단위 정수 환산. */
    public long getFileSizeMaxMb() {
        return fileSizeMax / (1024L * 1024L);
    }

    /**
     * 통합 게시판(전체글 보기) 여부. {@code groupedBoardIds} 가 비어있지 않으면 true.
     * true 인 마스터는 작성/수정/삭제가 거부되고, list/count 만 대상 게시판들의 글을 합쳐 보여준다.
     */
    public boolean isAggregator() {
        return groupedBoardIds != null && !groupedBoardIds.isBlank();
    }

    /**
     * 통합 게시판 모드일 때 대상 bbs_master_id 의 list 반환. 일반 모드면 빈 list.
     * CSV 를 split + trim + 빈값 제거한 결과.
     */
    public java.util.List<String> getGroupedBoardIdList() {
        if (!isAggregator()) return java.util.Collections.emptyList();
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String s : groupedBoardIds.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }
}
