package com.gonet.primary.content.service;

import com.gonet.primary.content.dto.Content;
import com.gonet.primary.content.dto.ContentHistory;
import com.gonet.primary.content.dto.ContentSaveForm;
import com.gonet.primary.content.dto.ContentSearch;
import com.gonet.primary.content.dto.ContentStatus;

import java.util.List;

/**
 * 콘텐츠 관리 서비스 — 관리자 도메인.
 *
 * <p>eGov 호환성: 인터페이스 + {@code ContentServiceImpl} (EgovAbstractServiceImpl 상속).
 * Controller 는 본 인터페이스만 주입받음 (DAO 직접호출 금지).
 *
 * <p>상태 전환은 {@link ContentStatus#canTransitionTo}. PUBLISHED 전환 시
 * {@code published_at} 자동 세팅, UNPUBLISHED 시 {@code unpublish_at} 자동 세팅.
 *
 * <p>버전 이력: UPDATE 시 기존 스냅샷을 tb_content_history 에 복사 후 version_no += 1.
 */
public interface ContentService {

    // 조회
    List<Content> search(ContentSearch search);
    int           count(ContentSearch search);
    Content       get(String contentId);

    // CUD
    String create(ContentSaveForm form);
    void   update(ContentSaveForm form);
    void   softDelete(String contentId);

    /**
     * body 필드만 갱신 — 미리보기 화면의 빠른 저장용. title/slug/menu/seo 등은 보존.
     * 일반 update() 와 동일하게 (a) 기존 값을 tb_content_history 에 스냅샷, (b) version_no += 1,
     * (c) 감사 이벤트 발행. 색인 동기화도 수행.
     *
     * @param changeNote 버전 이력 메모. null/blank 허용
     */
    void updateBody(String contentId, String body, String changeNote);

    /**
     * 상태 전환 — DRAFT→REVIEW→APPROVED→PUBLISHED→UNPUBLISHED 의 허용된 전이만.
     * 호출 측이 타겟 상태를 지정.
     *
     * @throws IllegalStateException 금지된 전이
     */
    void changeStatus(String contentId, ContentStatus target);

    /** 특정 콘텐츠의 버전 이력 — 최신 version_no 순. */
    List<ContentHistory> listHistory(String contentId);

    /** 특정 이력 레코드 단건. */
    ContentHistory getHistory(String contentHistoryId);
}
