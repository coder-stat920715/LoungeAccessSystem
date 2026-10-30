package com.lounge.access.dto;

import java.util.List;

public enum SpendCategory {
    RETAIL, TRAVEL, DINING, GROCERY, UTILITIES, ENTERTAINMENT,
    CASH_WITHDRAWAL, WALLET_RELOAD, FUEL;

    private static final List<SpendCategory> NON_QUALIFYING =
            List.of(CASH_WITHDRAWAL, WALLET_RELOAD, FUEL);

    /** Categories that never count toward lounge spend thresholds. */
    public static List<SpendCategory> nonQualifying() {
        return NON_QUALIFYING;
    }
}
