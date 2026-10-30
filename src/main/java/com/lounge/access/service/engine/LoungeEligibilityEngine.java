package com.lounge.access.service.engine;

import com.lounge.access.dto.DenialReason;
import com.lounge.access.entity.Card;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Strategy-pattern entry point: resolves the matching bank strategy and evaluates the presented card. */
@Slf4j
@Component
public class LoungeEligibilityEngine {

    private final List<EligibilityStrategy> strategies;

    public LoungeEligibilityEngine(List<EligibilityStrategy> strategies) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(EligibilityStrategy::priority))
                .toList();
    }

    public EligibilityDecision evaluate(EligibilityContext ctx) {
        Card card = ctx.card();
        if (!card.isActive() || card.getExpiryDate().isBefore(ctx.today())) {
            return EligibilityDecision.deny(DenialReason.CARD_INACTIVE,
                    "Card is blocked or expired", 0);
        }
        return strategies.stream()
                .filter(s -> s.supports(card))
                .findFirst()
                .map(s -> {
                    log.debug("Evaluating card {} with strategy {}", card.getId(), s.name());
                    return s.evaluate(ctx);
                })
                .orElseGet(() -> EligibilityDecision.deny(DenialReason.UNSUPPORTED_CARD,
                        "No lounge programme is configured for " + card.getBank() + " "
                                + card.getCardType() + " " + card.getProductCode(), 0));
    }
}
