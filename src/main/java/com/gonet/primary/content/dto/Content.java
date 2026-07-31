package com.gonet.primary.content.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_content 엔티티 — 관리자 CRUD 풀 DTO.
 *
 * <p>상태/게시 컬럼:
 * <ul>
 *   <li>{@code status} — {@link ContentStatus} (DRAFT/REVIEW/APPROVED/PUBLISHED/UNPUBLISHED)</li>
 *   <li>{@code publishedAt} — PUBLISHED 전환 시 자동 세팅</li>
 *   <li>{@code publishScheduledAt} — 예약 게시 (배치가 소비, v1 스코프 외)</li>
 *   <li>{@code unpublishAt} — 게시 중단 예정 (배치 소비)</li>
 * </ul>
 *
 * <p>원본/렌더 이원화: {@code originalContent} 는 원본 Markdown, {@code body} 는 렌더된 HTML.
 * 관리자는 Markdown 또는 HTML 중 하나를 저장 — 양방향 렌더링은 후속 스프린트.
 *
 * <p>표시용 JOIN 필드(비-엔티티): {@code siteCode}, {@code siteName}, {@code menuName}.
 */
@Getter
@Setter
public class Content extends BaseEntity implements SoftDeletable {

    private String contentId;
    private String siteId;
    private String menuId;

    private String title;
    private String slug;
    private String body;
    private String originalContent;
    private String summary;
    /** 디스크 HTML 원문 SHA-256 (hex). 동기화 변경 감지용 — 같으면 skip. */
    private String bodyHash;

    private String metaKeywords;
    private String metaDescription;

    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime publishScheduledAt;
    private LocalDateTime unpublishAt;

    private long viewCount;
    private int  versionNo;

    private String deleteYn;

    private String siteCode;
    private String siteName;
    private String menuName;

    public ContentStatus statusEnum() {
        return ContentStatus.safeParse(status);
    }
}
