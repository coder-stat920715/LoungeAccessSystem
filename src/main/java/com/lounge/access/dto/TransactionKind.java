package com.lounge.access.dto;

/** PURCHASE = debit to the card; PAYMENT / REFUND = credits (neither is treated as spend). */
public enum TransactionKind {
    PURCHASE, PAYMENT, REFUND
}
