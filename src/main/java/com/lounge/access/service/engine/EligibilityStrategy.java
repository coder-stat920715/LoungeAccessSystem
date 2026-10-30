package com.lounge.access.service.engine;

import com.lounge.access.entity.Card;

/** One bank/product rule set. The engine picks the lowest-priority-number strategy that supports the card. */
public interface EligibilityStrategy {

    String name();

    boolean supports(Card card);

    EligibilityDecision evaluate(EligibilityContext context);

    /** Lower value = evaluated first. Fallback strategies should return a large number. */
    default int priority() {
        return 100;
    }
}
