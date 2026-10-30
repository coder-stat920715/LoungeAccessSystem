package com.lounge.access.service.impl;

import com.lounge.access.dto.AnalyticsSummaryResponse;
import com.lounge.access.dto.AnalyticsSummaryResponse.BankStats;
import com.lounge.access.dto.AnalyticsSummaryResponse.HourBucket;
import com.lounge.access.dto.AnalyticsSummaryResponse.TierStats;
import com.lounge.access.dto.Bank;
import com.lounge.access.dto.DenialReason;
import com.lounge.access.dto.VisitStatus;
import com.lounge.access.repository.LoungeVisitRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final String UNKNOWN = "UNKNOWN";
    private static final int TOP_TIERS = 5;
    private static final int PEAK_HOURS = 3;

    private final LoungeVisitRepository loungeVisitRepository;
    private final Clock clock;

    /** Aggregates all access attempts of the last {@code hours} hours. */
    public AnalyticsSummaryResponse summary(int hours) {
        LocalDateTime end = LocalDateTime.now(clock);
        LocalDateTime start = end.minusHours(hours);

        Map<VisitStatus, Long> byStatus = new EnumMap<>(VisitStatus.class);
        for (Object[] row : loungeVisitRepository.countByStatusSince(start)) {
            byStatus.put((VisitStatus) row[0], ((Number) row[1]).longValue());
        }
        long redeemed = byStatus.getOrDefault(VisitStatus.REDEEMED, 0L);
        long approved = byStatus.getOrDefault(VisitStatus.APPROVED, 0L) + redeemed;
        long denied = byStatus.getOrDefault(VisitStatus.DENIED, 0L);
        long total = approved + denied;

        return new AnalyticsSummaryResponse(
                start, end, total, approved, redeemed, denied,
                percent(approved, total), percent(denied, total),
                denialsByReason(start),
                bankStats(start),
                topTiers(start),
                peakHours(hourly(start)),
                hourly(start));
    }

    private Map<String, Long> denialsByReason(LocalDateTime since) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (DenialReason reason : DenialReason.values()) {
            result.put(reason.name(), 0L);
        }
        for (Object[] row : loungeVisitRepository.countDenialsByReasonSince(since)) {
            if (row[0] != null) {
                result.put(((DenialReason) row[0]).name(), ((Number) row[1]).longValue());
            }
        }
        return result;
    }

    private List<BankStats> bankStats(LocalDateTime since) {
        Map<String, long[]> acc = new LinkedHashMap<>();   // [approved, denied]
        for (Object[] row : loungeVisitRepository.countByBankAndStatusSince(since)) {
            String bank = row[0] == null ? UNKNOWN : ((Bank) row[0]).name();
            VisitStatus status = (VisitStatus) row[1];
            long count = ((Number) row[2]).longValue();
            long[] counters = acc.computeIfAbsent(bank, k -> new long[2]);
            if (status == VisitStatus.DENIED) {
                counters[1] += count;
            } else {
                counters[0] += count;
            }
        }
        List<BankStats> result = new ArrayList<>();
        acc.forEach((bank, c) -> result.add(new BankStats(bank, c[0] + c[1], c[0], c[1])));
        result.sort(Comparator.comparingLong(BankStats::attempts).reversed());
        return result;
    }

    private List<TierStats> topTiers(LocalDateTime since) {
        List<TierStats> result = new ArrayList<>();
        for (Object[] row : loungeVisitRepository.countByBankAndProductSince(since)) {
            result.add(new TierStats(
                    row[0] == null ? UNKNOWN : ((Bank) row[0]).name(),
                    row[1] == null ? UNKNOWN : (String) row[1],
                    ((Number) row[2]).longValue()));
        }
        return result.stream().limit(TOP_TIERS).toList();
    }

    private List<HourBucket> hourly(LocalDateTime since) {
        long[] counts = new long[24];
        for (Object[] row : loungeVisitRepository.countByHourSince(since)) {
            counts[((Number) row[0]).intValue()] = ((Number) row[1]).longValue();
        }
        List<HourBucket> buckets = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            buckets.add(new HourBucket(h, String.format("%02d:00-%02d:59", h, h), counts[h]));
        }
        return buckets;
    }

    private List<HourBucket> peakHours(List<HourBucket> hourly) {
        return hourly.stream()
                .filter(b -> b.attempts() > 0)
                .sorted(Comparator.comparingLong(HourBucket::attempts).reversed()
                        .thenComparingInt(HourBucket::hour))
                .limit(PEAK_HOURS)
                .toList();
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0.0 : Math.round(part * 10000.0 / total) / 100.0;
    }
}
