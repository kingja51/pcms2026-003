package com.gonet.primary.system.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 메뉴 등록/수정 폼.
 *
 * <p>타입별 필수 필드 검증은 Service 계층에서 수행:
 * <ul>
 *   <li>CONTENT/BOARD → linkTargetId 필수</li>
 *   <li>URL           → linkUrl 필수</li>
 *   <li>FOLDER        → 링크 필드 모두 null 강제</li>
 * </ul>
 */
@Getter
@Setter
public class MenuSaveForm {

    private String menuId;
    private String siteId;
    private String parentMenuId;

    @NotBlank @Size(max = 100)
    private String menuName;

    @NotBlank @Pattern(regexp = "^(CONTENT|BOARD|URL|FOLDER)$",
                       message = "menuType: CONTENT/BOARD/URL/FOLDER")
    private String menuType;

    @Size(max = 40)
    private String linkTargetId;

    @Size(max = 1000)
    private String linkUrl;

    private String authRequiredYn = "N";
    private String useYn = "Y";
}
