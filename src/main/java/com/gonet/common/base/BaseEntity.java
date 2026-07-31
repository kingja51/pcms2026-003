package com.gonet.common.base;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 모든 도메인 엔티티의 상위 타입 — 6종 감사컬럼 의무 필드.
 *
 * <pre>
 *   created_by  / created_ip  / created_at
 *   updated_by  / updated_ip  / updated_at
 * </pre>
 *
 * <p>{@code delete_yn} 은 soft delete 대상 엔티티에만 포함 (하위 클래스에서 확장),
 * 본 BaseEntity 에는 포함하지 않음 — VIEW 나 단순 읽기용 DTO 에서도 상속 가능하도록.
 *
 * <p>AuditInterceptor(MyBatis)가 INSERT/UPDATE 시 자동 세팅.
 */
@Getter
@Setter
public abstract class BaseEntity {

    protected String        createdBy;
    protected String        createdIp;
    protected LocalDateTime createdAt;

    protected String        updatedBy;
    protected String        updatedIp;
    protected LocalDateTime updatedAt;
}
