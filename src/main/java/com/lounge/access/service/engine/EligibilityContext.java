package com.lounge.access.service.engine;

import com.lounge.access.entity.Card;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EligibilityContext(Card card, String loungeId, String voucherCode, LocalDateTime now) {

    public LocalDate today() {
        return now.toLocalDate();
    }
}
