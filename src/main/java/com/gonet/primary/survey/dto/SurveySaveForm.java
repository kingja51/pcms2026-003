package com.gonet.primary.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SurveySaveForm {

    private String surveyId;

    @NotBlank
    @Size(max = 40)
    private String surveyMasterId;

    @NotBlank
    @Size(max = 200)
    private String surveyTitle;

    @Size(max = 65535)
    private String surveyDescription;

    @NotNull
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;

    @Pattern(regexp = "^(DRAFT|PUBLISHED|CLOSED)$",
             message = "status 는 DRAFT/PUBLISHED/CLOSED 중 하나")
    private String status = "DRAFT";

    private String anonymousYn;
    private String oneResponseYn;
    private String useYn;

    /**
     * 문항 + 선택지 묶음을 JSON 으로 받음.
     * 형식 예:
     *   [
     *     {"text":"질문1","type":"RADIO","required":"Y","options":[{"text":"좋음"},{"text":"나쁨"}]},
     *     {"text":"점수","type":"SCALE","required":"Y","scaleMin":1,"scaleMax":5}
     *   ]
     * Service 단에서 Jackson 으로 파싱.
     */
    @Size(max = 100000)
    private String questionsJson;

    public static String yn(String raw) {
        return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
    }
}
