package com.lounge.access.controller;

import com.lounge.access.dto.AnalyticsSummaryResponse;
import com.lounge.access.dto.AnalyticsSummaryResponse.HourBucket;
import com.lounge.access.service.impl.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Real-time approval / denial statistics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Totals, approval vs rejection rate, denials by reason, bank distribution, "
            + "top card tiers and peak entry hours")
    public AnalyticsSummaryResponse summary(
            @RequestParam(defaultValue = "24") @Min(1) @Max(8760) int hours) {
        return analyticsService.summary(hours);
    }

    @GetMapping("/denials")
    @Operation(summary = "Denied attempts grouped by denial reason")
    public Map<String, Long> denials(@RequestParam(defaultValue = "24") @Min(1) @Max(8760) int hours) {
        return analyticsService.summary(hours).denialsByReason();
    }

    @GetMapping("/peak-hours")
    @Operation(summary = "Busiest entry hours of the day")
    public List<HourBucket> peakHours(@RequestParam(defaultValue = "24") @Min(1) @Max(8760) int hours) {
        return analyticsService.summary(hours).peakEntryHours();
    }
}
