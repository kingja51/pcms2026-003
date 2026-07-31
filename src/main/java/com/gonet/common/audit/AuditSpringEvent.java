package com.gonet.common.audit;

import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} 래퍼 — 5경로 중 3번째 (프로세스 내 리스너).
 *
 * <p>향후 OTel exporter, Kafka publisher 등이 {@code @EventListener} 로 수신.
 */
public class AuditSpringEvent extends ApplicationEvent {

    private final AuditEvent event;

    public AuditSpringEvent(Object source, AuditEvent event) {
        super(source);
        this.event = event;
    }

    public AuditEvent getEvent() { return event; }
}
