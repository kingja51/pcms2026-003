package com.gonet.primary.system.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 템플릿 등록/수정 폼 — 2026-04-23c 부터 전역 카탈로그.
 *
 * <p>{@code layoutPath} 는 Thymeleaf fragment 경로 (.html 생략):
 * 예) {@code "front/layouts/modern"} → {@code templates/front/layouts/modern.html}.
 */
@Getter
@Setter
public class TemplateSaveForm {

    private String templateId;

    @NotBlank @Size(max = 50)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]+$",
             message = "템플릿 코드는 대문자/숫자/언더스코어만 사용 (첫 글자 대문자)")
    private String templateCode;

    @NotBlank @Size(max = 100)
    private String templateName;

    @NotBlank @Size(max = 500)
    @Pattern(regexp = "^[a-zA-Z0-9_/-]+$",
             message = "레이아웃 경로는 영숫자/언더스코어/대시/슬래시만 사용 (경로 traversal 금지)")
    private String layoutPath;

    @Size(max = 500)
    private String description;

    /** Claude Design MD — 디자인 가이드/프롬프트 원문. TEXT. 길이 상한은 DB TEXT(최대 65535). */
    @Size(max = 65535)
    private String designMd;

    /**
     * 캡쳐 이미지 등 첨부의 file_group_id — 폼 GET 단계에서 사전 발급 (FG0_…).
     * file-picker 의 entityId/file_group_id 로 사용. Service 가 tb_template.file_group_id 에 저장.
     */
    @Size(max = 40)
    private String fileGroupId;

    /**
     * file-picker hidden input 값 — 업로드된 파일의 fileId 가 JSON 배열 형식.
     * 화면 동기화 용도. tb_template 에 컬럼으로 저장하지 않는다.
     */
    @Size(max = 1000)
    private String imagePickerJson;

    private String useYn = "Y";
}
