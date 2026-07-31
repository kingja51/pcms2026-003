package com.gonet.primary.system.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodeGroupSaveForm {

    private String codeGroupId;      // 수정 시 hidden

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]+$",
             message = "그룹 코드는 대문자/숫자/언더스코어만 (첫 글자 대문자)")
    private String groupCode;

    @NotBlank @Size(max = 100)
    private String groupName;

    @Size(max = 500)
    private String description;

    private String useYn = "Y";
}
