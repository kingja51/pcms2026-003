package com.gonet.primary.banner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BannerSaveForm {

    private String bannerId;

    @NotBlank
    @Size(max = 40)
    private String siteId;

    @NotBlank
    @Size(max = 200)
    private String bannerTitle;

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "banner_location 은 영문 대문자/숫자/언더스코어만")
    private String bannerLocation = "HEADER";

    /**
     * file-picker 의 entityId/file_group_id 로 사용 — 폼 GET 단계에서 사전 발급 (FG0_…).
     * Service 가 tb_banner.file_group_id 컬럼에 그대로 저장.
     */
    @Size(max = 40)
    private String fileGroupId;

    /**
     * file-picker hidden input 값 — 업로드된 파일의 fileId 가 JSON 배열 형식.
     * 화면에 첨부 카드 표시·삭제 동기화 용도. tb_banner 에 컬럼으로 저장하지 않는다.
     */
    @Size(max = 1000)
    private String imagePickerJson;

    @Size(max = 500)
    private String altText;

    /**
     * 외부 링크 URL — 스킴 화이트리스트 강제 (P0 XSS 방어).
     * 허용: http(s) / 절대 경로(/) / 앵커(#) / mailto / tel. 빈 값 허용.
     * {@code javascript:}, {@code data:}, {@code vbscript:} 등은 거부.
     */
    @Size(max = 1000)
    @Pattern(regexp = "^$|^(https?://|/|#|mailto:|tel:).*",
             message = "linkUrl 은 http(s)/절대경로/앵커/mailto/tel 만 허용")
    private String linkUrl;

    @Pattern(regexp = "^(_self|_blank)$",
             message = "link_target 는 _self / _blank 중 하나")
    private String linkTarget = "_self";

    @NotNull
    private LocalDateTime showFrom;

    @NotNull
    private LocalDateTime showTo;

    @Min(value = 0, message = "정렬 순서는 0 이상")
    private int sortOrder = 0;

    private String useYn;

    public static String yn(String raw) {
        return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
    }
}
