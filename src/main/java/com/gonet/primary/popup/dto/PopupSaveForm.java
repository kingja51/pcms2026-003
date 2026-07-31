package com.gonet.primary.popup.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 팝업 등록/수정 폼.
 *
 * <p>요일 입력은 체크박스 다중 선택 — controller 에서 콤마 join 후 Service 로 전달
 * (예: "MON,TUE,FRI"). 빈 값은 "전 요일 노출" 의미.
 *
 * <p>{@code popupContent} 는 HTML 허용 — 관리자만 입력하므로 기본 신뢰. 화면 출력 시
 * Thymeleaf {@code th:utext} 를 쓰되, OWASP Sanitizer 적용 여부는 후속 강화.
 */
@Getter
@Setter
public class PopupSaveForm {

    private String popupId;

    @NotBlank
    @Size(max = 40)
    private String siteId;

    @NotBlank
    @Size(max = 200)
    private String popupTitle;

    @Size(max = 65535)
    private String popupContent;

    @NotBlank
    @Pattern(regexp = "^(LAYER|MODAL|WINDOW|BANNER)$",
             message = "popup_type 는 LAYER/MODAL/WINDOW/BANNER 중 하나")
    private String popupType = "LAYER";

    /**
     * 외부 링크 URL — 스킴 화이트리스트 강제 (P0 XSS 방어).
     * 허용: http(s) / 절대 경로(/) / 앵커(#) / mailto / tel. 빈 값 허용.
     * {@code javascript:}, {@code data:}, {@code vbscript:} 등은 거부.
     */
    @Size(max = 1000)
    @Pattern(regexp = "^$|^(https?://|/|#|mailto:|tel:).*",
             message = "linkUrl 은 http(s)/절대경로/앵커/mailto/tel 만 허용")
    private String linkUrl;

    /**
     * file-picker 의 entityId/file_group_id 로 사용 — 폼 GET 단계에서 사전 발급 (FG0_…).
     * Service 가 tb_popup.file_group_id 컬럼에 그대로 저장.
     */
    @Size(max = 40)
    private String fileGroupId;

    /**
     * file-picker hidden input 값 — 업로드된 파일의 fileId 가 JSON 배열 형식으로 들어옴.
     * 화면에 첨부 카드 표시·삭제 동기화 용도. tb_popup 에 컬럼으로 저장하지 않는다.
     * 예: {@code ["019d-..."]}.
     */
    @Size(max = 1000)
    private String imagePickerJson;

    private Integer widthPx;
    private Integer heightPx;
    private Integer positionX;
    private Integer positionY;

    @NotNull
    private LocalDateTime showFrom;

    @NotNull
    private LocalDateTime showTo;

    /** 요일 콤마 CSV — 빈 값 = 전 요일. 예: "MON,TUE,WED" */
    @Size(max = 20)
    private String showDays;

    private LocalTime showTimeFrom;
    private LocalTime showTimeTo;

    @Min(value = 0, message = "쿠키 유효일은 0 이상")
    private int cookieDays = 1;

    @Min(value = 0, message = "정렬 순서는 0 이상")
    private int sortOrder = 0;

    private String useYn;

    public static String yn(String raw) {
        return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
    }
}
