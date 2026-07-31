package com.gonet.primary.system.access.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_role_url_access 1건 — URL 접근 규칙 DTO.
 *
 * <p>중요 컬럼:
 * <ul>
 *   <li>{@code accessType} : PERMIT_ALL/AUTHENTICATED/ANONYMOUS/ROLE/AUTH/IP_ONLY/DENY</li>
 *   <li>{@code requiredRoles} : ROLE 타입용 role_id CSV (하나라도 매칭 시 통과)</li>
 *   <li>{@code requiredAuths} : AUTH 타입용 auth_id CSV</li>
 *   <li>{@code allowedUserTypes} : user_type CSV (MEMBER/EMPLOYEE/ADMIN) 추가 필터</li>
 *   <li>{@code allowedIps} : IP_ONLY 용 CIDR CSV</li>
 *   <li>{@code priority} : 낮을수록 먼저 매칭 (전역 규칙을 앞에 배치)</li>
 * </ul>
 *
 * <p>{@code roleUrlAccessId} 는 PK 로, {@code url_access_id} 컬럼에 매핑된다 —
 * 명시 별칭이 mapper XML 에 사용된다.
 */
@Getter
@Setter
public class RoleUrlAccessRule {

    public static final String PERMIT_ALL     = "PERMIT_ALL";
    public static final String AUTHENTICATED  = "AUTHENTICATED";
    public static final String ANONYMOUS      = "ANONYMOUS";
    public static final String ROLE           = "ROLE";
    public static final String AUTH           = "AUTH";
    public static final String IP_ONLY        = "IP_ONLY";
    public static final String DENY           = "DENY";

    public static final String METHOD_ALL     = "ALL";

    /** PK — tb_role_url_access.url_access_id 와 매핑 (mapper XML 에서 alias) */
    private String  roleUrlAccessId;
    private String  siteId;
    /** JOIN tb_site — viewer 표시용 */
    private String  siteCode;
    private String  siteName;
    private String  urlPattern;
    private String  httpMethod;
    private String  accessType;
    private String  requiredRoles;
    private String  requiredAuths;
    private String  allowedUserTypes;
    private String  allowedIps;
    private String  requireCsrfYn;
    private String  require2faYn;
    private int     priority;
    private String  description;
    private String  remarks;
    private String  useYn;
    private String  deleteYn;

    private String        createdBy;
    private String        createdIp;
    private LocalDateTime createdAt;
    private String        updatedBy;
    private String        updatedIp;
    private LocalDateTime updatedAt;

    public boolean requires2fa() { return "Y".equalsIgnoreCase(require2faYn); }
    public boolean requiresCsrf() { return "Y".equalsIgnoreCase(requireCsrfYn); }
    public boolean isMethodAll() { return METHOD_ALL.equalsIgnoreCase(httpMethod); }
    public boolean isActive()    { return "Y".equalsIgnoreCase(useYn); }
}
