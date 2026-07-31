package com.gonet.primary.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 관리자 접속 허용 IP 등록/수정 폼.
 *
 * <p>{@code ipType} 별 입력 규칙 (Service 가 추가 검증):
 * <ul>
 *   <li>SINGLE — {@code ipAddress} 필수 (IPv4 점-십진수)</li>
 *   <li>RANGE  — {@code ipStart}, {@code ipEnd} 필수, start ≤ end</li>
 *   <li>CIDR   — {@code ipAddress} 가 {@code 10.0.0.0/8} 형태</li>
 * </ul>
 */
@Getter
@Setter
public class AdminAllowIpSaveForm {

    private String ipId;             // 수정 시 hidden

    @NotBlank @Size(max = 40)
    private String adminId;          // 경로변수에서 주입 (hidden)

    @NotBlank
    @Pattern(regexp = "^(SINGLE|RANGE|CIDR)$",
             message = "ipType: SINGLE / RANGE / CIDR")
    private String ipType = "SINGLE";

    @Size(max = 50)
    private String ipAddress;        // SINGLE / CIDR

    @Size(max = 50)
    private String ipStart;          // RANGE only

    @Size(max = 50)
    private String ipEnd;            // RANGE only

    @Size(max = 200)
    private String description;

    /** 만료 일시 (선택) — yyyy-MM-dd'T'HH:mm 형식 권장 (브라우저 datetime-local 호환). */
    private LocalDateTime expiresAt;

    private String useYn = "Y";
}
