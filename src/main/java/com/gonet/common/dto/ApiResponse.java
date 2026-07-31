package com.gonet.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통 API 응답 래퍼 — {@code ApiController} (REST) 에서 사용.
 *
 * <pre>
 * { "success": true, "message": "성공", "data": { ... } }
 * </pre>
 *
 * <p>MngController/UsrController 는 HTML/HX-Redirect 흐름이므로 본 래퍼를 사용하지 않는다.
 */
@Schema(description = "REST API 공통 응답 래퍼. 모든 ApiController 가 본 래퍼로 직렬화.")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    @Schema(description = "성공 여부 — false 이면 message 에 사유, data 는 null", example = "true")
    private boolean success;

    @Schema(description = "사람이 읽는 메시지 — 성공 시 '성공' 또는 도메인 설명, 실패 시 에러 사유",
        example = "성공")
    private String  message;

    @Schema(description = "응답 본문 — 도메인별 DTO 또는 List/Map. 실패 시 null")
    private T       data;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("성공")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> fail(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
