package com.lounge.access.service.engine;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardScheme;
import com.lounge.access.dto.CardType;
import com.lounge.access.entity.Card;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** "Today" is fixed at 2026-10-29: preceding 3 months = 2026-07-01..09-30, previous quarter = same range. */
final class TestFixtures {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 10, 0);
    static final LocalDate WINDOW_START = LocalDate.of(2026, 7, 1);
    static final LocalDate WINDOW_END = LocalDate.of(2026, 9, 30);

    private TestFixtures() {
    }

    static Card card(Bank bank, CardType type, String product, LocalDate issueDate) {
        return Card.builder()
                .id(1L)
                .bank(bank)
                .cardType(type)
                .scheme(CardScheme.VISA)
                .productCode(product)
                .issueDate(issueDate)
                .expiryDate(NOW.toLocalDate().plusYears(3))
                .active(true)
                .build();
    }

    static EligibilityContext ctx(Card card, String voucherCode) {
        return new EligibilityContext(card, "HYD-T1-ENCORE", voucherCode, NOW);
    }
}
