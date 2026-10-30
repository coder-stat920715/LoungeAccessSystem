package com.lounge.access.dto;

public enum DenialReason {
    INSUFFICIENT_SPEND,
    EXPIRED_VOUCHER,
    INVALID_VOUCHER,
    QUOTA_EXCEEDED,
    UNSUPPORTED_CARD,
    CARD_NOT_FOUND,
    CARD_INACTIVE,
    CUSTOMER_MISMATCH
}
