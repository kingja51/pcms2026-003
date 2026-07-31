package com.gonet.primary.file.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 파일 목록 검색 조건.
 *
 * <p>필터: 엔티티 타입, 검사 상태(PENDING/CLEAN/INFECTED/ERROR/ALL), 원본 파일명 keyword.
 */
@Getter
@Setter
public class FileSearch extends PageRequest {

    private String entityType;
    private String siteId;
    private String virusScanStatus;
}
