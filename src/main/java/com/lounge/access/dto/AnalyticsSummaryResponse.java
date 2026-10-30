package com.lounge.access.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AnalyticsSummaryResponse(
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        long totalAttempts,
        long approved,
        long redeemed,
        long denied,
        double approvalRatePercent,
        double rejectionRatePercent,
        Map<String, Long> denialsByReason,
        List<BankStats> byBank,
        List<TierStats> topCardTiers,
        List<HourBucket> peakEntryHours,
        List<HourBucket> hourlyDistribution) {

    /** "approved" includes redeemed passes. */
    public record BankStats(String bank, long attempts, long approved, long denied) {
    }

    public record TierStats(String bank, String cardTier, long attempts) {
    }

    public record HourBucket(int hour, String label, long attempts) {
    }
}
