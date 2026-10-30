package com.lounge.access.dto;

import java.time.LocalDateTime;

public record RedeemPassResponse(
        Long visitId,
        String passToken,
        String loungeId,
        VisitStatus status,
        LocalDateTime redeemedAt,
        String message) {
}
