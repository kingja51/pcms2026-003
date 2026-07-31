package com.gonet.primary.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 그룹 등록/수정 폼.
 */
@Getter
@Setter
public class AdminGroupSaveForm {

    private String adminGroupId;       // 수정 시 hidden

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]+$",
             message = "그룹 코드는 대문자/숫자/언더스코어만 (첫 글자 대문자)")
    private String groupCode;

    @NotBlank @Size(max = 100)
    private String groupName;

    @Size(max = 500)
    private String description;

    @Size(max = 40)
    private String defaultRoleId;

    @Size(max = 2000)
    private String ipWhitelist;

    /** HH:mm 24h format, 비어 있으면 제약 없음. */
    @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$",
             message = "허용 시작 시각은 HH:mm 형식이어야 합니다.")
    private String allowedTimeFrom;

    @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$",
             message = "허용 종료 시각은 HH:mm 형식이어야 합니다.")
    private String allowedTimeTo;

    @Pattern(regexp = "^[YN]$")
    private String twoFactorRequired = "Y";

    @Size(max = 40)
    private String passwordPolicyId;

    private String useYn = "Y";
}
