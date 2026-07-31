package com.gonet.common.calendar;

import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Thymeleaf 캘린더 렌더링용 주(week) 뷰모델.
 *
 * <p>일요일 ~ 토요일 7일 — 한국 캘린더 관습. 기준 날짜가 포함된 주를 자동 산출.
 *
 * <p>각 day 의 {@code key} 는 {@code yyyy-MM-dd} 문자열 — 호스트 컨트롤러가 그 날짜에
 * 매핑되는 holiday/schedule 리스트를 Map&lt;String, List&lt;?&gt;&gt; 로 넘겨주면 렌더링 가능.
 */
@Getter
public class CalendarWeek {

    private final LocalDate ref;
    private final LocalDate today;
    private final LocalDate weekStart;   // Sunday
    private final LocalDate weekEnd;     // Saturday
    private final List<Day> days;

    public CalendarWeek(LocalDate ref, LocalDate today) {
        this.ref   = ref;
        this.today = today;
        // 일요일 시작 정렬
        int back = ref.getDayOfWeek().getValue() % 7; // SUN=0 ... SAT=6
        this.weekStart = ref.minusDays(back);
        this.weekEnd   = weekStart.plusDays(6);

        List<Day> ds = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            ds.add(new Day(date, date.equals(today), date.getDayOfWeek()));
        }
        this.days = List.copyOf(ds);
    }

    public LocalDate getPrev()      { return weekStart.minusWeeks(1); }
    public LocalDate getNext()      { return weekStart.plusWeeks(1); }
    public LocalDate getGridStart() { return weekStart; }
    public LocalDate getGridEnd()   { return weekEnd; }

    @Getter
    public static class Day {
        private final LocalDate date;
        private final boolean   today;
        private final DayOfWeek dow;
        public Day(LocalDate date, boolean today, DayOfWeek dow) {
            this.date  = date;
            this.today = today;
            this.dow   = dow;
        }
        public String getKey()       { return date.toString(); }
        public boolean isSunday()    { return dow == DayOfWeek.SUNDAY; }
        public boolean isSaturday()  { return dow == DayOfWeek.SATURDAY; }
        public int getDayOfMonth()   { return date.getDayOfMonth(); }
        public int getMonthValue()   { return date.getMonthValue(); }
    }
}
