package com.lounge.access.dto;

/** Spend categories recognised in card statements. Non-qualifying ones never count toward lounge thresholds. */
public enum StatementCategory {
    SHOPPING(true), GROCERY(true), DINING(true), TRAVEL(true), ENTERTAINMENT(true), UTILITIES(true),
    HEALTH(true), EDUCATION(true), INSURANCE(true), OTHER(true),
    FUEL(false), WALLET_RELOAD(false), CASH_WITHDRAWAL(false), FEES_AND_CHARGES(false);

    private final boolean qualifying;

    StatementCategory(boolean qualifying) {
        this.qualifying = qualifying;
    }

    public boolean isQualifying() {
        return qualifying;
    }
}
