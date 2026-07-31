package com.gonet.logging.security.alert;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gonet.logging.security.dto.SecurityEventSeverity;
import com.gonet.logging.security.dto.SecurityLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 텔레그램 Bot API 로 보안 이벤트를 알림 — Bot API {@code sendMessage}.
 *
 * <p>활성 조건: {@code gopcms.alert.telegram.enabled=true} + bot-token + chat-id 설정.
 * 비활성 시 본 빈 미등록 → {@link NoOpSecurityAlertService} 가 폴백.
 *
 * <p>전송 로직:
 * <ol>
 *   <li>row.severity 가 props.minSeverity 이상이면 전송, 아니면 skip</li>
 *   <li>HTML parse_mode 메시지 구성 — 특수문자 escape</li>
 *   <li>{@code POST https://api.telegram.org/bot<TOKEN>/sendMessage} JSON body</li>
 *   <li>실패는 WARN 로그만 — 본 흐름(보안 이벤트 적재) 차단 X. fallback logger 흡수</li>
 * </ol>
 *
 * <p>비동기는 호출자({@code SecurityLoggerImpl.writeAsync} — {@code @Async("accessLogExecutor")})
 * 에서 이미 보장되므로 본 메서드는 동기 실행. 별도 executor 추가 부담 회피.
 *
 * <p>외부 API 신뢰성: 5xx/네트워크 실패 시 단순 try-catch — Resilience4j retry/circuit breaker 는
 * 후속 강화. 1단계는 알림 누락 < 본 트랜잭션 보존 우선.
 */
@Service("telegramSecurityAlertService")
@ConditionalOnProperty(prefix = "gopcms.alert.telegram", name = "enabled", havingValue = "true")
public class TelegramSecurityAlertService implements SecurityAlertService {

    private static final Logger log = LoggerFactory.getLogger(TelegramSecurityAlertService.class);
    /** SIEM 직접 수집용 fallback 로거 — RollingFileAppender 분리 권장 (§0.48 동일 패턴). */
    private static final Logger fallback = LoggerFactory.getLogger("com.gonet.security.fallback");

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient client;
    private final TelegramAlertProperties props;
    /**
     * Dedup 캐시 — 같은 {@code (event_type, actor)} 키가 window 내 재발생 시 silent drop.
     * Caffeine LRU + expireAfterWrite. props.dedupWindowSeconds <= 0 면 null (dedup 비활성).
     */
    private final Cache<String, Boolean> dedupCache;

    public TelegramSecurityAlertService(@Qualifier("telegramRestClient") RestClient telegramRestClient,
                                         TelegramAlertProperties props) {
        this.client = telegramRestClient;
        this.props  = props;
        this.dedupCache = (props.getDedupWindowSeconds() > 0)
            ? Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(props.getDedupWindowSeconds()))
                .maximumSize(Math.max(100L, props.getDedupMaxKeys()))
                .build()
            : null;
    }

    @Override
    public void notifyIfApplicable(SecurityLog row) {
        if (row == null) return;
        if (!props.isEffectivelyEnabled()) return;     // 봇 토큰/chatId 미설정

        // severity 임계 — 이상이어야 전송 (CRITICAL > WARN > INFO 순)
        if (!shouldNotify(row.getSeverity())) return;

        // §0.52 dedup — 같은 (event_type, actor) 키가 window 내 재발생 시 텔레그램만 silent drop
        // (DB INSERT 는 SecurityLoggerImpl.writeAsync 에서 이미 commit 됨 — 영향 없음)
        if (isDuplicateWithinWindow(row)) {
            log.info("SECURITY_ALERT_TELEGRAM_DEDUP event_type={} actor={} ip={}",
                row.getEventType(), row.getActorId(), row.getClientIp());
            return;
        }

        String text = buildMessage(row);
        try {
            send(text);
            log.info("===SECURITY_ALERT_TELEGRAM_OK event_type={} severity={}",
                row.getEventType(), row.getSeverity());
        } catch (RestClientException ex) {
            // 텔레그램 API 실패 — 본 흐름 차단 안 함
            fallback.error("SECURITY_ALERT_TELEGRAM_FAIL event_type={} severity={} actor={} reason={}",
                row.getEventType(), row.getSeverity(), row.getActorId(), redactToken(ex.getMessage()));
            log.warn("SECURITY_ALERT_TELEGRAM_FAIL event_type={} reason={}",
                row.getEventType(), redactToken(ex.getMessage()));
        } catch (Exception ex) {
            // 메시지 빌드 등 예상 외 실패 — 동일 정책
            fallback.error("SECURITY_ALERT_TELEGRAM_BUILD_FAIL event_type={} reason={}",
                row.getEventType(), redactToken(ex.getMessage()));
        }
    }

    /**
     * row.severity 가 props.minSeverity 이상이면 true.
     * enum ordinal 비교 — INFO(0) < WARN(1) < CRITICAL(2)
     */
    private boolean shouldNotify(String severityName) {
        if (severityName == null) return false;
        SecurityEventSeverity sev;
        try {
            sev = SecurityEventSeverity.valueOf(severityName);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return sev.ordinal() >= props.getMinSeverity().ordinal();
    }

    /**
     * HTML parse_mode 메시지 구성. 텔레그램 HTML 은 &lt;b&gt;/&lt;i&gt;/&lt;code&gt;/&lt;pre&gt; 만 지원,
     * 본문 내 {@code <}/{@code >}/{@code &} 는 entity 로 escape.
     */
    private String buildMessage(SecurityLog row) {
        String emoji = "CRITICAL".equalsIgnoreCase(row.getSeverity()) ? "🚨"
                     : "WARN".equalsIgnoreCase(row.getSeverity())     ? "⚠️"
                     : "ℹ️";
        StringBuilder sb = new StringBuilder(512);
        sb.append(emoji).append(" <b>[").append(escapeHtml(row.getSeverity()))
          .append("] ").append(escapeHtml(row.getEventType())).append("</b>\n");
        if (row.getLoggedAt() != null) {
            sb.append("시각: <code>").append(TS.format(row.getLoggedAt())).append("</code>\n");
        }
        if (row.getActorId() != null) {
            sb.append("사용자: <code>").append(escapeHtml(row.getActorId())).append("</code>\n");
        }
        if (row.getClientIp() != null) {
            sb.append("IP: <code>").append(escapeHtml(row.getClientIp())).append("</code>\n");
        }
        if (row.getRequestUri() != null) {
            // requestHost 가 있으면 풀 URL (예: http://127.0.0.1/admin/login), 없으면 path 만
            String host = row.getRequestHost();
            String fullUri = (host != null && !host.isBlank())
                ? host + row.getRequestUri()
                : row.getRequestUri();
            sb.append("URI: <code>").append(escapeHtml(fullUri)).append("</code>\n");
        }
        if (row.getDetailJson() != null) {
            sb.append("상세:\n<pre>").append(escapeHtml(row.getDetailJson())).append("</pre>\n");
        }
        if (row.getTraceId() != null) {
            sb.append("trace_id: <code>").append(escapeHtml(row.getTraceId())).append("</code>");
        }

        // 텔레그램 4096자 한도 — 안전 측 trim. trim 하면 HTML 태그 잘림 위험 있어 마지막 닫는 태그까지는 보존
        String full = sb.toString();
        int max = Math.max(200, props.getMaxMessageLength());
        if (full.length() > max) {
            return full.substring(0, max) + "\n…(truncated)";
        }
        return full;
    }

    /**
     * sendMessage POST — JSON body.
     * URL: {@code /bot<token>/sendMessage}
     */
    private void send(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", props.getChatId());
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);

        client.post()
              .uri("/bot{token}/sendMessage", props.getBotToken())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .toBodilessEntity();
    }

    /** Bot API 토큰 정규식 — {@code bot123456:AA...} 형태. */
    private static final java.util.regex.Pattern BOT_TOKEN_IN_URL =
        java.util.regex.Pattern.compile("bot\\d+:[A-Za-z0-9_-]+");

    /**
     * 예외 메시지에서 봇 토큰 제거. RestClient 예외 메시지에는 요청 URL
     * ({@code https://api.telegram.org/bot<TOKEN>/sendMessage}) 이 그대로 박혀
     * 토큰이 로그/SIEM 으로 유출된다. 실제 토큰 값 + URL 패턴 양쪽을 마스킹.
     */
    private String redactToken(String msg) {
        if (msg == null) return null;
        String token = props.getBotToken();
        String out = (token != null && !token.isBlank()) ? msg.replace(token, "***") : msg;
        return BOT_TOKEN_IN_URL.matcher(out).replaceAll("bot***");
    }

    /** 텔레그램 HTML mode 의 5 entity 만 escape — 다른 special char 는 본문 그대로 노출. */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // -----------------------------------------------------------------
    // dedup — §0.52 알림 폭주 방지
    // -----------------------------------------------------------------

    /**
     * 같은 {@code (event_type, actor)} 키가 dedup window 내 재발생 시 true (silent drop).
     * 첫 진입은 false 반환 + 캐시에 마킹. props.dedupWindowSeconds <= 0 (cache null) 이면 항상 false.
     *
     * <p>actor 폴백: actorId 가 SYSTEM/ANONYMOUS/null/blank 이면 client_ip 사용.
     * 둘 다 없으면 "unknown" (전역 1건만 — 거의 발생 안 하는 코너 케이스).
     */
    private boolean isDuplicateWithinWindow(SecurityLog row) {
        if (dedupCache == null) return false;
        String key = buildDedupKey(row);
        if (key == null) return false;
        // putIfAbsent: 첫 진입은 null 반환 → 새 알림 발송. 이미 있으면 non-null → drop.
        Boolean prev = dedupCache.asMap().putIfAbsent(key, Boolean.TRUE);
        return prev != null;
    }

    private static String buildDedupKey(SecurityLog row) {
        if (row == null || row.getEventType() == null) return null;
        String actor = row.getActorId();
        if (actor == null || actor.isBlank()
                || "SYSTEM".equalsIgnoreCase(actor)
                || "ANONYMOUS".equalsIgnoreCase(actor)) {
            actor = row.getClientIp();
        }
        if (actor == null || actor.isBlank()) actor = "unknown";
        return row.getEventType() + ":" + actor;
    }

    /**
     * 단위 테스트용 — dedup 캐시 상태 확인.
     * 캐시 비활성(props.dedupWindowSeconds &lt;= 0) 시 -1.
     */
    public long dedupCacheSize() {
        return (dedupCache == null) ? -1L : dedupCache.estimatedSize();
    }
}
