package com.gonet.logging.security.alert;

import com.gonet.logging.security.dto.SecurityEventSeverity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보안 이벤트 텔레그램 알림 설정 — application.yml {@code gopcms.alert.telegram.*}.
 *
 * <p>운영 알림 정책:
 * <ul>
 *   <li>{@link SecurityEventSeverity#CRITICAL} — 즉시 알림 (기본)</li>
 *   <li>{@link SecurityEventSeverity#WARN}     — {@code min-severity=WARN} 으로 lower 시 활성</li>
 *   <li>{@link SecurityEventSeverity#INFO}     — {@code min-severity=INFO} 으로 lower 시 활성 (트래픽 폭주 주의)</li>
 * </ul>
 *
 * <p>봇 토큰은 BotFather (@BotFather) 에서 발급. chat-id 는 그룹/채널/개인 채팅 ID.
 * 그룹 채팅은 음수 prefix (예: -100123456789).
 */
@Getter
@Setter
@ConfigurationProperties("gopcms.alert.telegram")
public class TelegramAlertProperties {

    /**
     * 알림 활성 토글. 기본 false — 운영 환경에서 명시적으로 true 로 켜야 함.
     * 미설정 시 빈 등록도 skip ({@code @ConditionalOnProperty}).
     */
    private boolean enabled = false;

    /** 텔레그램 Bot API 토큰 — BotFather 에서 발급. 비어 있으면 알림 비활성. */
    private String botToken;

    /** 알림을 받을 chat_id — 그룹/채널/개인. 비어 있으면 알림 비활성. */
    private String chatId;

    /** API 베이스 URL — 정상 환경은 default 사용. */
    private String baseUrl = "https://api.telegram.org";

    /** 최소 알림 severity — 이 이상의 이벤트만 전송. 기본 CRITICAL. */
    private SecurityEventSeverity minSeverity = SecurityEventSeverity.CRITICAL;

    /** 메시지 본문 길이 상한 (텔레그램 limit 4096 — 안전 측 3500). */
    private int maxMessageLength = 3500;

    /** 연결/응답 타임아웃 (ms). */
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs    = 5000;

    /**
     * Dedup window (초) — 같은 {@code (event_type, actor)} 키가 본 시간 안에 재발생하면
     * 텔레그램은 첫 1건만 전송, 이후 silent drop. DB INSERT 는 영향 없음.
     *
     * <p>예: 한 공격자가 60초 내 100 URL 스캔으로 PRIVILEGE_ESCALATION 100건 발생해도
     * 텔레그램은 첫 1건만. window 초과 시 같은 키 재발생하면 다시 1건.
     *
     * <p>0 이하 = dedup 비활성 (모든 이벤트 알림).
     */
    private int dedupWindowSeconds = 60;

    /** Dedup 캐시 최대 키 수 — 무한 키 입력 메모리 보호. LRU evict. */
    private long dedupMaxKeys = 10_000L;

    /**
     * 환경에서 잘못 설정해도 안전하게 기동하도록 — botToken/chatId 미설정 시
     * 알림을 자동 비활성으로 본다.
     */
    public boolean isEffectivelyEnabled() {
        return enabled
            && botToken != null && !botToken.isBlank()
            && chatId   != null && !chatId.isBlank();
    }
}
