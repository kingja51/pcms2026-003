package com.gonet.primary.board.article.service;

import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.dto.BbsArticleSaveForm;
import com.gonet.primary.board.article.dto.BbsArticleSearch;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.system.login.dto.CustomUserDetails;

import java.util.List;

/**
 * 게시글 서비스 — 회원/관리자 공통 진입점.
 *
 * <p>책임:
 * <ul>
 *   <li>write_auth / read_auth 검증 (BoardMaster 정책 기반)</li>
 *   <li>secret 글 접근 제어 — 작성자 본인 또는 ROLE_STAFF 이상</li>
 *   <li>notice_yn 토글은 ROLE_STAFF 이상만 가능 (일반 사용자가 보내도 무시)</li>
 *   <li>view_count 증가는 호출 측 cookie/세션 중복 체크 통과 후만</li>
 *   <li>감사 이벤트 발행 (BBS_ARTICLE_CREATE/UPDATE/DELETE/STATUS/NOTICE)</li>
 * </ul>
 */
public interface BoardArticleService {

    List<BbsArticle> search(BbsArticleSearch search);

    int count(BbsArticleSearch search);

    /** 단건 — 게시판/게시글 ID 짝 검증. 사용자 화면 우선. */
    BbsArticle get(String bbsMasterId, String articleId);

    /** 단건 — articleId 만 (관리자 화면 — 게시판 ID 모를 때 사용). */
    BbsArticle getById(String articleId);

    /** 이전글(더 과거) — 같은 게시판 PUBLISHED 1건. 상세 하단 내비용. 없으면 null. */
    BbsArticle getPrev(String bbsMasterId, BbsArticle current);

    /** 다음글(더 최신) — 같은 게시판 PUBLISHED 1건. 상세 하단 내비용. 없으면 null. */
    BbsArticle getNext(String bbsMasterId, BbsArticle current);

    /**
     * 회원/직원/관리자 작성 — write_auth 검증.
     *
     * @return 신규 article_id (UUID v7)
     */
    String create(BbsArticleSaveForm form, String clientIp);

    /**
     * 본인/관리자 수정. 작성자가 아니고 관리자도 아니면 거부.
     */
    void update(BbsArticleSaveForm form, String clientIp);

    /**
     * 본인/관리자 삭제 (soft).
     */
    void softDelete(String articleId);

    /** 관리자 상태 전환 — PUBLISHED ↔ HIDDEN ↔ REPORTED. */
    void adminUpdateStatus(String articleId, String status);

    /** 관리자 공지 토글. */
    void adminToggleNotice(String articleId, boolean notice);

    /**
     * view_count 1 증가. 호출 측이 cookie 등 중복 방지 통과 후만 호출.
     */
    void incrementViewCount(String articleId);

    /**
     * 게시글에 첨부된 파일 목록 — soft-delete 만 제외, virus_scan_status 무관 노출
     * (작성/수정/상세 화면에서 PENDING 도 보이도록).
     *
     * @param fileGroupId tb_bbs_article.file_group_id
     */
    List<FileItem> listAttachments(String fileGroupId);

    /**
     * 작성자 식별자({@code tb_bbs_article.created_by}) lightweight lookup.
     * OWNER_PRIVACY 정책 평가에서 본인 검증용. 미존재/blank 시 null.
     */
    String findCreatedBy(String articleId);

    /**
     * 게시판 읽기 권한 검증 — {@code tb_bbs_master.read_auth} 정책 강제.
     *
     * <ul>
     *   <li>{@code ALL}      — 비로그인 포함 누구나 통과</li>
     *   <li>{@code MEMBER}   — 로그인 사용자 (MEMBER/EMPLOYEE/ADMIN)</li>
     *   <li>{@code EMPLOYEE} — EMPLOYEE/ADMIN</li>
     *   <li>{@code ADMIN}    — ADMIN 만</li>
     * </ul>
     *
     * <p>위반 시 {@link org.springframework.security.access.AccessDeniedException} 발생.
     * Spring Security 가 비인증이면 401→로그인 페이지, 인증되었으나 권한 부족이면 403 처리.
     *
     * @param master 게시판 마스터
     * @param me     현재 사용자 (비인증 null)
     * @throws org.springframework.security.access.AccessDeniedException 권한 부족
     */
    void ensureCanRead(BbsMaster master, CustomUserDetails me);

    /**
     * 게시판 쓰기 권한 검증 — {@code tb_bbs_master.write_auth} 정책 강제.
     * 비로그인 작성은 미지원 — write_auth 와 무관하게 인증 필수.
     *
     * @param master 게시판 마스터
     * @param me     현재 사용자 (비인증 null)
     */
    void ensureCanWrite(BbsMaster master, CustomUserDetails me);

    /**
     * 회원 대시보드 — 본인이 작성한 최신 게시글.
     * created_by = userId AND delete_yn='N'. PUBLISHED/HIDDEN/REPORTED 모두 포함.
     */
    List<BbsArticle> findRecentByCreatedBy(String userId, int limit);
}
