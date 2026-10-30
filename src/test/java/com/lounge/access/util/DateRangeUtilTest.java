package com.lounge.access.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.lounge.access.util.DateRangeUtil.DateRange;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRangeUtilTest {

    @Test
    void precedingThreeMonthsExcludeCurrentMonth() {
        DateRange range = DateRangeUtil.precedingCalendarMonths(LocalDate.of(2026, 10, 29), 3);

        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void precedingMonthsCrossYearBoundary() {
        DateRange range = DateRangeUtil.precedingCalendarMonths(LocalDate.of(2026, 2, 10), 3);

        assertThat(range.start()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void currentQuarterIsDerivedFromMonth() {
        DateRange q = DateRangeUtil.currentQuarter(LocalDate.of(2026, 5, 15));

        assertThat(q.start()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(q.end()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void previousQuarterOfFirstQuarterIsLastQuarterOfPriorYear() {
        DateRange q = DateRangeUtil.previousQuarter(LocalDate.of(2026, 1, 5));

        assertThat(q.start()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(q.end()).isEqualTo(LocalDate.of(2025, 12, 31));
    }
}
