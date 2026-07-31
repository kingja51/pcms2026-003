package com.gonet.primary.content.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_content_history — 콘텐츠 버전 스냅샷 1건.
 *
 * <p>UPDATE 직전에 현재 {@link Content} 상태를 이곳에 복사 — version_no 유일 제약.
 */
@Getter
@Setter
public class ContentHistory extends BaseEntity {

    private String contentHistoryId;
    private String contentId;
    private int    versionNo;

    private String title;
    private String body;
    private String summary;

    private String changedBy;
    private String changeNote;

    private String changedByName;
}
