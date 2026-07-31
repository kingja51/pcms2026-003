package com.gonet.common.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Thymeleaf 전역 날짜 포맷 유틸리티 — `@fmt.dt(obj)` 로 호출.
 *
 * <p>문제: Thymeleaf 의 {@code th:text="${obj.createdAt}"} 는 Spring ConversionService 를
 * 거치지 않고 {@code LocalDateTime.toString()} 을 그대로 출력 → ISO-8601 "2026-04-24T14:10:41".
 *
 * <p>해결: 본 빈을 통해 명시적으로 포매팅 — 모든 템플릿이 동일 포맷
 * ({@code yyyy-MM-dd HH:mm:ss}) 로 렌더되도록 강제. null 은 {@code "-"} 로 치환.
 *
 * <p>사용 예:
 * <pre>
 *   &lt;span th:text="${&#64;fmt.dt(site.createdAt)}"&gt;-&lt;/span&gt;
 *   &lt;td  th:text="${&#64;fmt.d(holiday.holidayDate)}"&gt;-&lt;/td&gt;
 *   &lt;td  th:text="${&#64;fmt.t(schedule.startTime)}"&gt;-&lt;/td&gt;
 * </pre>
 */
@Component("fmt")
public class Fmt {

    public static final String DASH = "-";

    private static final DateTimeFormatter DT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DTM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter D   = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter YM  = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter T   = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TM  = DateTimeFormatter.ofPattern("HH:mm");

    /** LocalDateTime/LocalDate/LocalTime 을 "yyyy-MM-dd HH:mm:ss" 로. null → "-". */
    public String dt(Object v) {
        if (v == null) return DASH;
        if (v instanceof LocalDateTime ldt) return ldt.format(DT);
        if (v instanceof LocalDate ld)      return ld.atStartOfDay().format(DT);
        if (v instanceof LocalTime lt)      return lt.format(T);
        return v.toString();
    }

    /** LocalDateTime 을 "yyyy-MM-dd HH:mm" 로 (초 절삭). null → "-". */
    public String dtm(Object v) {
        if (v == null) return DASH;
        if (v instanceof LocalDateTime ldt) return ldt.format(DTM);
        if (v instanceof LocalDate ld)      return ld.atStartOfDay().format(DTM);
        if (v instanceof LocalTime lt)      return lt.format(TM);
        return v.toString();
    }

    /** LocalDate/LocalDateTime 을 "yyyy-MM" 로. null → "-". */
    public String ym(Object v) {
        if (v == null) return DASH;
        if (v instanceof LocalDate ld)      return ld.format(YM);
        if (v instanceof LocalDateTime ldt) return ldt.toLocalDate().format(YM);
        return v.toString();
    }

    /** LocalDate 또는 LocalDateTime 을 "yyyy-MM-dd" 로. null → "-". */
    public String d(Object v) {
        if (v == null) return DASH;
        if (v instanceof LocalDate ld)      return ld.format(D);
        if (v instanceof LocalDateTime ldt) return ldt.toLocalDate().format(D);
        return v.toString();
    }

    /** LocalTime 또는 LocalDateTime 을 "HH:mm:ss" 로. null → "-". */
    public String t(Object v) {
        if (v == null) return DASH;
        if (v instanceof LocalTime lt)      return lt.format(T);
        if (v instanceof LocalDateTime ldt) return ldt.toLocalTime().format(T);
        return v.toString();
    }
}
