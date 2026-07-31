package com.gonet.primary.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ScheduleSaveForm {

    private String scheduleId;

    /** 마스터 ID — Controller 가 PathVariable 에서 자동 주입. site/menu 컨텍스트는 master 가 결정. */
    @NotBlank
    @Size(max = 40)
    private String scheduleMasterId;

    @NotBlank
    @Size(max = 200)
    private String scheduleTitle;

    @Size(max = 65535)
    private String scheduleContent;

    @Size(max = 30)
    private String scheduleCategory;

    @NotNull
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;

    @Size(max = 200)
    private String location;

    /**
     * 외부 링크 URL — 스킴 화이트리스트 강제 (P0 XSS 방어).
     * 허용: http(s) / 절대 경로(/) / 앵커(#) / mailto / tel. 빈 값 허용.
     * {@code javascript:}, {@code data:}, {@code vbscript:} 등은 거부.
     */
    @Size(max = 1000)
    @Pattern(regexp = "^$|^(https?://|/|#|mailto:|tel:).*",
             message = "linkUrl 은 http(s)/절대경로/앵커/mailto/tel 만 허용")
    private String linkUrl;

    private String allDayYn;

    @Pattern(regexp = "^(#[0-9a-fA-F]{6})?$",
             message = "color_code 는 #RRGGBB 형식")
    @Size(max = 10)
    private String colorCode;

    private String useYn;

    public static String yn(String raw) {
        return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
    }
}
