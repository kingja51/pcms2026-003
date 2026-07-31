package com.gonet.primary.system.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodeSaveForm {

    private String codeId;           // 수정 시 hidden

    @NotBlank @Size(max = 40)
    private String codeGroupId;

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Z0-9][A-Z0-9_]*$",
             message = "코드는 대문자/숫자/언더스코어만 사용")
    private String code;

    @NotBlank @Size(max = 100)
    private String codeName;

    @Size(max = 500)
    private String codeValue;

    @PositiveOrZero
    private Integer sortOrder = 0;

    private String useYn = "Y";
}
