package com.gonet.common.calendar;

import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Thymeleaf 캘린더 렌더링용 월(month) 뷰모델.
 *
 * <p>일요일~토요일 7열 × 6행(최대) 격자. 시작/끝 패딩 셀은 인접 월 날짜로 채워짐 ({@code inMonth=false}).
 *
 * <p>각 셀의 {@code keyDate} 는 {@code yyyy-MM-dd} 문자열 — 호스트 컨트롤러가 그날짜에
 * 매핑되는 holiday/schedule 리스트를 별도 Map<String, List<?>> 로 같이 넘겨주면 그릴 수 있다.
 */
@Getter
public class CalendarMonth {

    private final YearMonth ym;
    private final LocalDate today;
    private final List<Week> weeks;
    private final LocalDate gridStart;
    private final LocalDate gridEnd;

    public CalendarMonth(YearMonth ym, LocalDate today) {
        this.ym    = ym;
        this.today = today;
        // 첫 주 일요일 시작 — 한국 캘린더 관습
        LocalDate first = ym.atDay(1);
        int leadDays = (first.getDayOfWeek().getValue() % 7); // SUN=0 ... SAT=6
        this.gridStart = first.minusDays(leadDays);
        // 6주 그리드 (42일) — 항상 동일 높이 보장
        this.gridEnd   = gridStart.plusDays(42 - 1);

        List<Week> ws = new ArrayList<>(6);
        for (int w = 0; w < 6; w++) {
            List<Day> days = new ArrayList<>(7);
            for (int d = 0; d < 7; d++) {
                LocalDate date = gridStart.plusDays(w * 7L + d);
                days.add(new Day(date,
                                  ym.equals(YearMonth.from(date)),
                                  date.equals(today),
                                  date.getDayOfWeek()));
            }
            ws.add(new Week(days));
        }
        this.weeks = List.copyOf(ws);
    }

    public YearMonth getPrev() { return ym.minusMonths(1); }
    public YearMonth getNext() { return ym.plusMonths(1); }

    @Getter
    public static class Week {
        private final List<Day> days;
        public Week(List<Day> days) { this.days = days; }
    }

    @Getter
    public static class Day {
        private final LocalDate date;
        private final boolean   inMonth;
        private final boolean   today;
        private final DayOfWeek dow;
        public Day(LocalDate date, boolean inMonth, boolean today, DayOfWeek dow) {
            this.date    = date;
            this.inMonth = inMonth;
            this.today   = today;
            this.dow     = dow;
        }
        /** Thymeleaf 가 Map 에서 꺼낼 때 쓰는 키 — yyyy-MM-dd. */
        public String getKey() { return date.toString(); }
        /** 일요일/토요일 분리 — CSS 색상에 활용. */
        public boolean isSunday()   { return dow == DayOfWeek.SUNDAY; }
        public boolean isSaturday() { return dow == DayOfWeek.SATURDAY; }
        public int getDayOfMonth()  { return date.getDayOfMonth(); }
    }
}
