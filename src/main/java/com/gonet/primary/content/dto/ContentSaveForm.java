package com.gonet.primary.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 콘텐츠 등록/수정 폼.
 *
 * <p>생성 시: {@code contentId} 는 서버가 UUID v7 로 발급 → 입력 무시.
 * 수정 시: 기존 {@code contentId} 를 hidden 필드로 제출.
 *
 * <p>상태 전환은 본 폼이 아닌 별도 엔드포인트({@code /admin/system/content/{id}/status})에서 처리 —
 * 저장 시에는 {@code status} 를 건드리지 않고 본문 필드만 수정.
 */
@Getter
@Setter
public class ContentSaveForm {

    private String contentId;

    @NotBlank
    @Size(max = 40)
    private String siteId;

    @Size(max = 40)
    private String menuId;

    @NotBlank
    @Size(max = 300)
    private String title;

    @Size(max = 200)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_\\-]*$",
             message = "slug 는 영문/숫자/하이픈/언더스코어만 사용 (첫 글자는 영문/숫자)")
    private String slug;

    @Size(max = 16_777_215, message = "body 는 최대 16MB 까지 입력 가능")
    private String body;

    @Size(max = 65_535, message = "originalContent 는 최대 64KB 까지 입력 가능")
    private String originalContent;

    @Size(max = 1000)
    private String summary;

    @Size(max = 500)
    private String metaKeywords;

    @Size(max = 500)
    private String metaDescription;

    /** 버전 이력 기록용 코멘트 — UPDATE 시에만 사용. */
    @Size(max = 500)
    private String changeNote;
}
