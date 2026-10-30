package com.lounge.access.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Calendar-window helpers shared by the eligibility strategies. */
public final class DateRangeUtil {

    private DateRangeUtil() {
    }

    /** Inclusive date range. */
    public record DateRange(LocalDate start, LocalDate end) {

        public LocalDateTime startOfDay() {
            return start.atStartOfDay();
        }

        /** Exclusive upper bound, convenient for timestamp comparisons. */
        public LocalDateTime endExclusive() {
            return end.plusDays(1).atStartOfDay();
        }
    }

    /** The {@code months} full calendar months before the month of {@code today} (current month excluded). */
    public static DateRange precedingCalendarMonths(LocalDate today, int months) {
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);
        return new DateRange(firstOfThisMonth.minusMonths(months), firstOfThisMonth.minusDays(1));
    }

    /** Calendar quarter (Jan-Mar, Apr-Jun, Jul-Sep, Oct-Dec) containing {@code today}. */
    public static DateRange currentQuarter(LocalDate today) {
        int firstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate start = LocalDate.of(today.getYear(), firstMonth, 1);
        return new DateRange(start, start.plusMonths(3).minusDays(1));
    }

    public static DateRange previousQuarter(LocalDate today) {
        LocalDate currentStart = currentQuarter(today).start();
        return new DateRange(currentStart.minusMonths(3), currentStart.minusDays(1));
    }
}
