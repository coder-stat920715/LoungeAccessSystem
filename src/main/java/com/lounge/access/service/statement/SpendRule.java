package com.lounge.access.service.statement;

import com.lounge.access.util.DateRangeUtil;
import com.lounge.access.util.DateRangeUtil.DateRange;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Bank/product rule expressed independently of the desk-time strategies, so a statement can be evaluated for any
 * future month. Windows mirror the strategies: evaluating "month M" is the same as a desk check on the first day of M.
 *
 * @param newCardWaiverMonths months after the issue month during which spend is waived; -1 = no waiver
 */
public record SpendRule(String name,
                        String description,
                        BigDecimal minimumSpend,
                        int visitsPerQuarter,
                        WindowKind windowKind,
                        int lookbackMonths,
                        int newCardWaiverMonths,
                        boolean voucherRequired) {

    public enum WindowKind { ROLLING_MONTHS, PREVIOUS_QUARTER }

    /** Spend window that decides eligibility during {@code month}. */
    public DateRange windowFor(YearMonth month) {
        return windowKind == WindowKind.ROLLING_MONTHS
                ? DateRangeUtil.precedingCalendarMonths(month.atDay(1), lookbackMonths)
                : DateRangeUtil.previousQuarter(month.atDay(1));
    }

    public List<YearMonth> windowMonths(YearMonth month) {
        DateRange range = windowFor(month);
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(range.start()); !m.isAfter(YearMonth.from(range.end())); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }
}
