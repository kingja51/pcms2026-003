package com.gonet.primary.file.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_file_group — 엔티티 단위 파일 묶음.
 *
 * <p>콘텐츠/게시판/회원 등 상위 엔티티와 N:1 파일을 묶어주는 중간 테이블.
 * {@code entity_type + entity_id} 로 상위 엔티티를 역참조.
 */
@Getter
@Setter
public class FileGroup extends BaseEntity implements SoftDeletable {

    private String fileGroupId;
    private String entityType;
    private String entityId;
    private String siteId;
    /** 다운로드 권한 — ANONYMOUS / ROLE_MEMBER / ROLE_EMPLOYEE / ROLE_ADMIN. 기본 ROLE_MEMBER. */
    private String downloadAuth = "ROLE_MEMBER";
    private String deleteYn;
}
