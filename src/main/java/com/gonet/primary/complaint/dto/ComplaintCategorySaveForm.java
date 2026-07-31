package com.gonet.primary.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComplaintCategorySaveForm {

    @NotBlank
    private String complaintMasterId;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,49}$",
             message = "영문 시작, 영문/숫자/언더스코어 1~50자")
    private String categoryCode;

    @NotBlank @Size(max = 100)
    private String categoryName;

    @Size(max = 300)
    private String description;

    private int    sortOrder = 0;
    private String useYn     = "Y";
}
