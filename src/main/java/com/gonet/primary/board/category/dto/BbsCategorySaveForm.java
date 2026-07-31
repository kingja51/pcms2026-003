package com.gonet.primary.board.category.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 카테고리 등록/수정 폼.
 */
@Getter
@Setter
public class BbsCategorySaveForm {

    private String categoryId;

    @NotBlank(message = "게시판을 선택해주세요.")
    private String bbsMasterId;

    @NotBlank(message = "카테고리 코드를 입력해주세요.")
    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
             message = "카테고리 코드는 영문/숫자/_/- 만 허용합니다.")
    private String categoryCode;

    @NotBlank(message = "카테고리 명을 입력해주세요.")
    @Size(min = 1, max = 100)
    private String categoryName;

    @Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
    private int sortOrder = 0;

    private String useYn = "Y";
}
