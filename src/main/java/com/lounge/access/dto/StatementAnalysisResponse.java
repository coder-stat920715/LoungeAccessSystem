package com.lounge.access.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Full explanation of a statement-based lounge-eligibility forecast. */
public record StatementAnalysisResponse(
        String bank,
        String cardType,
        String productCode,
        String targetMonth,
        String ruleApplied,
        String ruleDescription,
        EligibilityStatus status,
        boolean eligibleForTargetMonth,
        String summary,
        SpendWindow window,
        BigDecimal qualifyingSpendInWindow,
        BigDecimal requiredSpend,
        /** Positive = surplus over the threshold, negative = shortfall. */
        BigDecimal surplusOrShortfall,
        double progressPercent,
        /** False when a window month has no transactions or the statements do not cover the whole window. */
        boolean dataComplete,
        QuotaInfo quota,
        List<MonthlyBreakdown> monthlyBreakdown,
        List<CategoryBreakdown> categoryBreakdown,
        List<MerchantSpend> topMerchants,
        ExcludedSpend excludedSpend,
        FollowUpPlan planForUpcomingMonths,
        List<String> insights,
        List<String> recommendations,
        List<String> warnings,
        List<String> assumptions,
        List<FileSummary> files) {

    public record SpendWindow(LocalDate start, LocalDate end, List<String> months) {
    }

    /** visitsPerQuarter / visitsAvailable of -1 mean unlimited. */
    public record QuotaInfo(int visitsPerQuarter, LocalDate quarterStart, LocalDate quarterEnd,
                            int visitsUsed, int visitsAvailable, boolean voucherRequired, String note) {
    }

    public record MonthlyBreakdown(String month, String label, boolean hasData, boolean includedInWindow,
                                   int transactionCount, BigDecimal totalPurchases, BigDecimal qualifyingSpend,
                                   BigDecimal nonQualifyingSpend, Map<String, BigDecimal> byCategory) {
    }

    public record CategoryBreakdown(String category, boolean qualifying, BigDecimal amount,
                                    double percentOfAllSpend, int transactionCount) {
    }

    public record MerchantSpend(String merchant, BigDecimal amount, int transactionCount,
                                double percentOfQualifyingSpend) {
    }

    public record TransactionView(LocalDate date, String description, BigDecimal amount, String category) {
    }

    public record ExcludedSpend(BigDecimal total, double percentOfAllSpend, Map<String, BigDecimal> byCategory,
                                List<TransactionView> largest) {
    }

    /**
     * What to do now so that a later month qualifies. {@code planMonth} is the first month whose window
     * includes the target month's spend (the following month for rolling-month rules, the next quarter-based
     * month for previous-quarter rules).
     */
    public record FollowUpPlan(String planMonth, boolean planMonthIsFollowingMonth, LocalDate windowStart,
                               LocalDate windowEnd, List<String> windowMonths, List<String> monthsAlreadyCompleted,
                               List<String> monthsStillToCome, BigDecimal spendAlreadyBanked,
                               BigDecimal requiredSpend, BigDecimal additionalQualifyingSpendNeeded,
                               BigDecimal perMonthTarget, BigDecimal perWeekTarget, boolean alreadySecured,
                               List<String> monthsLeavingWindow, BigDecimal spendLeavingWindow,
                               String followingMonthOutlook) {
    }

    public record FileSummary(String fileName, String format, int transactionsParsed, int linesSkipped,
                              LocalDate firstTransactionDate, LocalDate lastTransactionDate) {
    }
}
