package com.gonet.primary.system.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 사이트 등록/수정 폼.
 *
 * <p>생성 시: {@code siteId} 는 서버가 UUID v7 로 발급 → 입력 무시.
 * <p>수정 시: 기존 {@code siteId} 를 hidden 필드로 제출.
 */
@Getter
@Setter
public class SiteSaveForm {

    private String siteId;

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]+$",
             message = "사이트 코드는 영문자/숫자/언더스코어만 사용 (첫 글자는 영문자). 대소문자 구별 없음")
    private String siteCode;

    @NotBlank @Size(max = 100)
    private String siteName;

    @Size(max = 255)
    private String domain;

    @NotBlank @Size(max = 10)
    private String defaultLang = "ko";

    @Size(max = 40)
    private String templateId;

    @Size(max = 500)
    private String description;

    @Size(max = 8000, message = "head meta 는 최대 8000자까지 입력 가능")
    private String headMeta;

    @Size(max = 1000, message = "copyright 는 최대 1000자까지 입력 가능")
    private String copyright;

    /**
     * 선택 테마 — {@code tb_theme.theme_id}. 빈 값이면 템플릿 기본 브랜드.
     *
     * <p>소속 검증은 {@code fk_site_theme(template_id, theme_id)} 복합 FK 가 한다 —
     * 다른 템플릿의 테마를 넣으면 저장 시 FK 위반으로 걸린다.
     *
     * <p><b>패턴 검증을 두지 않는다.</b> 이전에는 테마가 문자열 코드였고
     * {@code ^$|^(theme-)?[A-Za-z][A-Za-z0-9-]{0,28}$} 가 붙어 있었는데,
     * FK 로 바뀐 뒤 그대로 두면 {@code THM_…} 같은 ID 가 <b>언더스코어 때문에
     * 반려</b>된다. 길이도 {@code varchar(40)} 에 맞춘다(구 {@code max=30} 은 짧다).
     */
    @Size(max = 40, message = "테마 ID 는 최대 40자")
    private String themeId;

    /** 선택 레이아웃 — {@code tb_layout.layout_id}. 빈 값이면 템플릿 기본 레이아웃. */
    @Size(max = 40, message = "레이아웃 ID 는 최대 40자")
    private String layoutId;

    private String useYn = "Y";
}
