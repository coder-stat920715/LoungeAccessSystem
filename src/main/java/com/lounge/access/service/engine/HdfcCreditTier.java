package com.lounge.access.service.engine;

import com.lounge.access.util.LoungeConstants;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** HDFC credit tiers: previous-quarter spend threshold and visits per quarter. */
public enum HdfcCreditTier {
    MILLENNIA(new BigDecimal("100000"), 1),
    REGALIA_GOLD(new BigDecimal("60000"), 3),
    INFINIA(BigDecimal.ZERO, LoungeConstants.UNLIMITED_VISITS),
    DINERS_BLACK(BigDecimal.ZERO, LoungeConstants.UNLIMITED_VISITS);

    private final BigDecimal minimumSpend;
    private final int visitsPerQuarter;

    HdfcCreditTier(BigDecimal minimumSpend, int visitsPerQuarter) {
        this.minimumSpend = minimumSpend;
        this.visitsPerQuarter = visitsPerQuarter;
    }

    public BigDecimal minimumSpend() {
        return minimumSpend;
    }

    public int visitsPerQuarter() {
        return visitsPerQuarter;
    }

    public static Optional<HdfcCreditTier> fromProductCode(String productCode) {
        if (productCode == null) {
            return Optional.empty();
        }
        String normalized = productCode.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return Arrays.stream(values()).filter(t -> t.name().equals(normalized)).findFirst();
    }
}
