package com.lounge.access.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record EligibilityResponse(
        Long visitId,
        boolean eligible,
        String reason,
        DenialReason denialReason,
        @Schema(description = "Visits left in the current quarter after this attempt; -1 means unlimited")
        int visitsRemaining,
        String loungePassToken,
        LocalDateTime passExpiresAt) {
}
