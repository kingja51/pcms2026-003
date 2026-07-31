package com.gonet.primary.system.code.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 폼 드롭다운용 경량 DTO — {@code code} / {@code codeName} 쌍 + 부가값.
 *
 * <p>Thymeleaf 에서 {@code @code.options('STATUS')} 로 받아 {@code <option>} 루프에 사용.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeOption {

    private String code;
    private String codeName;
    private String codeValue;
    private Integer sortOrder;
}
