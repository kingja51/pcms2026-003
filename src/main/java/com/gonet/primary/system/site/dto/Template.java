package com.gonet.primary.system.site.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_template 1건 — CRUD 풀 엔티티.
 *
 * <p>2026-04-23c 부터 템플릿은 <b>전역 카탈로그</b>. 사이트에 소속되지 않고
 * {@link Site#templateId} 로 사이트가 선택.
 *
 * <p>경량 버전 {@link TemplateInfo} 와 역할 분리:
 * <ul>
 *   <li>{@link TemplateInfo} : layout 참조만 필요한 런타임 조회용</li>
 *   <li>{@link Template}     : 관리자 화면/엑셀/감사 필드 포함</li>
 * </ul>
 */
@Getter
@Setter
public class Template extends BaseEntity implements SoftDeletable, UseFlagged {

    private String templateId;
    private String templateCode;
    private String templateName;
    private String layoutPath;
    private String designMd;          // Claude Design MD — 디자인 가이드 원문
    private String fileGroupId;       // 캡쳐 이미지 등 첨부 파일 그룹 ID (file-picker)
    private String description;
    private String useYn;
    private String deleteYn;
}
