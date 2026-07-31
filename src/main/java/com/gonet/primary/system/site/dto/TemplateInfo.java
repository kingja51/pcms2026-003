package com.gonet.primary.system.site.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * tb_template 경량 DTO — SiteContext 의 선택된 레이아웃 정보.
 *
 * <p>{@link #layoutPath} 가 Thymeleaf 렌더링 시 실제 레이아웃 fragment 경로.
 * 예: {@code "templates/front/layouts/modern"} → {@code templates/front/layouts/modern.html}.
 *
 * <p>2026-04-23c 부터 템플릿은 전역 카탈로그이므로 siteId 제거.
 */
@Getter
@Setter
public class TemplateInfo implements java.io.Serializable {

    private String templateId;
    private String templateCode;
    private String templateName;
    private String layoutPath;
    private String useYn;
}
