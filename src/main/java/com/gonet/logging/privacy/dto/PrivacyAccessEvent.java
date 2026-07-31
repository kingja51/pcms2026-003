package com.gonet.logging.privacy.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 개인정보 접근 이벤트 — {@link com.gonet.logging.privacy.service.PrivacyAccessLogger} 입력.
 *
 * <p>{@link com.gonet.common.audit.PrivacyAccessAspect} 가 어노테이션 메서드 진입 시 채우거나,
 * 호출부에서 fluent {@code withXxx} 체이닝으로 직접 만들 수 있다.
 *
 * <p>fluent 체이닝을 위해 setter 들이 {@code this} 를 반환하지 않는 일반 setter 를 갖되,
 * {@code withXxx} 별칭 메서드도 제공한다 ({@link com.gonet.common.audit.AuditEvent} 와 동일 패턴).
 */
@Getter
@Setter
public class PrivacyAccessEvent {

    // Who — 미세팅 시 PrivacyAccessLogger 가 SecurityContext 에서 자동 보강
    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;

    // Where — 미세팅 시 PrivacyAccessLogger 가 RequestContextHolder 에서 자동 보강
    private String clientIp;
    private String userAgent;
    private String sessionId;
    private String requestUri;
    private String httpMethod;
    private String traceId;

    // 정보주체
    private String  targetEntity;
    private String  targetUserType;
    private String  targetId;
    private Integer targetCount;

    // 처리 내용
    private String piiFields;
    private String accessAction;
    private String accessReason;
    private String result;
    private String failReason;

    public static PrivacyAccessEvent of(String accessAction, String targetEntity) {
        PrivacyAccessEvent e = new PrivacyAccessEvent();
        e.accessAction = accessAction;
        e.targetEntity = targetEntity;
        e.result       = PrivacyAccessLog.RESULT_SUCCESS;
        e.targetCount  = 1;
        return e;
    }

    public PrivacyAccessEvent withTarget(String userType, String id) {
        this.targetUserType = userType;
        this.targetId = id;
        return this;
    }

    public PrivacyAccessEvent withCount(int count) {
        this.targetCount = count;
        return this;
    }

    public PrivacyAccessEvent withFields(String csv) {
        this.piiFields = csv;
        return this;
    }

    public PrivacyAccessEvent withReason(String reason) {
        this.accessReason = reason;
        return this;
    }

    public PrivacyAccessEvent withResult(String result) {
        this.result = result;
        return this;
    }

    public PrivacyAccessEvent withFailReason(String reason) {
        this.failReason = reason;
        return this;
    }
}
