package com.gonet.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 엑셀 다운로드 요청 DTO — {@link PageRequest} 를 상속해 검색조건을 재사용한다.
 *
 * <p>감사 추적을 위해 {@code downloadReason} 은 필수(10자 이상). 컨트롤러는 이 값을
 * {@code AuditLogger} 의 {@code afterJson} 에 포함시켜 {@code log_audit} 에 기록한다.
 */
@Getter
@Setter
public class ExcelDownloadRequest extends PageRequest {

    @NotBlank(message = "다운로드 사유를 입력해주세요.")
    @Size(min = 10, max = 500, message = "다운로드 사유는 10자 이상 입력해주세요.")
    private String downloadReason;
}
