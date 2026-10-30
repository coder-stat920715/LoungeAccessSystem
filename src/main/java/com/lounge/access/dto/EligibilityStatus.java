package com.lounge.access.dto;

public enum EligibilityStatus {
    ELIGIBLE,
    ELIGIBLE_NEW_CARD_EXCEPTION,
    /** Spend criteria are met but every visit of the quarter has been used. */
    QUOTA_EXHAUSTED,
    NOT_ELIGIBLE,
    NOT_SUPPORTED
}
