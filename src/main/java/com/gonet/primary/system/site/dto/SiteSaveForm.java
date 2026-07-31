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
    private String defaultTemplateId;

    @Size(max = 500)
    private String description;

    @Size(max = 8000, message = "head meta 는 최대 8000자까지 입력 가능")
    private String headMeta;

    @Size(max = 1000, message = "copyright 는 최대 1000자까지 입력 가능")
    private String copyright;

    /** KRDS 테마 클래스 — "theme-" 접두 생략 입력 허용(서비스에서 보정). 빈값 = 템플릿 기본 브랜드. */
    @Size(max = 30)
    @Pattern(regexp = "^$|^(theme-)?[A-Za-z][A-Za-z0-9-]{0,28}$",
             message = "테마는 영문자/숫자/하이픈만 사용 (예: theme-navy). 비우면 템플릿 기본 브랜드")
    private String theme;

    private String useYn = "Y";
}
