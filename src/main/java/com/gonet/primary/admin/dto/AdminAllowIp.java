package com.gonet.primary.admin.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_admin_allow_ip 1건 — 관리자별 접속 허용 IP 화이트리스트.
 *
 * <p>{@code ipType} 별 매칭 규칙:
 * <ul>
 *   <li>SINGLE — {@code ipAddress} 와 정확히 일치</li>
 *   <li>RANGE  — {@code ipStart} ≤ 클라이언트 IP ≤ {@code ipEnd} (IPv4 long 비교)</li>
 *   <li>CIDR   — {@code ipAddress} 가 CIDR 표기 (예: 10.0.0.0/8) — 비트마스크 비교</li>
 * </ul>
 *
 * <p>로그인 시 `tb_admin_allow_ip` 에 활성 행이 있으면 해당 IP 만 허용,
 * 없으면 기존 {@code tb_admin.ip_whitelist} CSV 로 폴백.
 */
@Getter
@Setter
public class AdminAllowIp extends BaseEntity implements SoftDeletable, UseFlagged {

    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_RANGE  = "RANGE";
    public static final String TYPE_CIDR   = "CIDR";

    private String        ipId;
    private String        adminId;
    private String        ipAddress;
    private String        ipType;
    private String        ipStart;
    private String        ipEnd;
    private String        description;
    private Integer       accessCount;
    private LocalDateTime lastAccessAt;
    private LocalDateTime expiresAt;
    private String        useYn;
    private String        deleteYn;
}
