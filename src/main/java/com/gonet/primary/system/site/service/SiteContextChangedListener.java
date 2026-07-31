package com.gonet.primary.system.site.service;

import com.gonet.primary.system.site.dto.SiteContextChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link SiteContextChangedEvent} 를 받아 Caffeine 캐시를 무효화.
 *
 * <p>{@link TransactionalEventListener} 로 커밋 후 실행 — 롤백된 변경이 캐시 무효화까지
 * 이어지지 않도록 방지. 트랜잭션 밖에서 발행된 이벤트는 {@link EventListener} fallback
 * 이 동작하도록 별도 핸들러 추가.
 */
@Component
public class SiteContextChangedListener {

    private static final Logger log = LoggerFactory.getLogger(SiteContextChangedListener.class);

    private final SiteContextService service;

    public SiteContextChangedListener(SiteContextService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContextChanged(SiteContextChangedEvent ev) {
        if (ev.isBulk()) {
            log.info("===SiteContext bulk evict (reason={})", ev.getReason());
            service.evictAll();
        } else {
            log.info("===SiteContext evict siteId={} (reason={})", ev.getSiteId(), ev.getReason());
            service.evict(ev.getSiteId());
        }
    }
}
