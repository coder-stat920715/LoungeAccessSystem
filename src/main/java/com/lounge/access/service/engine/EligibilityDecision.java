package com.lounge.access.service.engine;

import com.lounge.access.dto.DenialReason;
import com.lounge.access.entity.Voucher;

/**
 * Outcome of a strategy evaluation.
 *
 * @param visitsRemaining visits left in the quarter after this attempt ({@link #UNLIMITED} = no cap)
 * @param voucherToConsume voucher that must be marked used when the decision is an approval (may be null)
 */
public record EligibilityDecision(boolean eligible,
                                  DenialReason denialReason,
                                  String message,
                                  int visitsRemaining,
                                  Voucher voucherToConsume) {

    public static final int UNLIMITED = -1;

    public static EligibilityDecision approve(String message, int visitsRemaining, Voucher voucher) {
        return new EligibilityDecision(true, null, message, visitsRemaining, voucher);
    }

    public static EligibilityDecision deny(DenialReason reason, String message, int visitsRemaining) {
        return new EligibilityDecision(false, reason, message, visitsRemaining, null);
    }
}
