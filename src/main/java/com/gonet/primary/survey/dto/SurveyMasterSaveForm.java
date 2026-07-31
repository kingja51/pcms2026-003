package com.gonet.primary.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurveyMasterSaveForm {

    private String surveyMasterId;

    @NotBlank
    @Size(max = 40)
    private String siteId;

    @Size(max = 40)
    private String menuId;

    @NotBlank
    @Size(max = 200)
    private String masterTitle;

    @Size(max = 65535)
    private String masterContent;

    private String useYn;

    public static String yn(String raw) {
        return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
    }
}
