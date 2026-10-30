package com.lounge.access.service.engine;

import com.lounge.access.config.LoungeProperties;
import com.lounge.access.entity.Card;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import com.lounge.access.util.DateRangeUtil;
import org.springframework.stereotype.Component;

/**
 * Fallback for banks without a dedicated strategy (configured via {@code lounge.generic.supported-banks}):
 * standard previous-quarter spend threshold with a quarterly visit cap. Banks not listed are UNSUPPORTED_CARD.
 */
@Component
public class GenericEligibilityStrategy extends AbstractSpendLinkedStrategy {

    private final LoungeProperties.Generic config;

    public GenericEligibilityStrategy(SpendHistoryRepository spendHistoryRepository,
                                      LoungeVisitRepository loungeVisitRepository,
                                      LoungeProperties properties) {
        super(spendHistoryRepository, loungeVisitRepository);
        this.config = properties.getGeneric();
    }

    @Override
    public String name() {
        return "GENERIC_FALLBACK";
    }

    @Override
    public boolean supports(Card card) {
        return config.getSupportedBanks().contains(card.getBank());
    }

    @Override
    public int priority() {
        return 1000;
    }

    @Override
    public EligibilityDecision evaluate(EligibilityContext ctx) {
        SpendPolicy policy = new SpendPolicy(
                config.getMinimumSpend(),
                DateRangeUtil.previousQuarter(ctx.today()),
                false,
                null,
                config.getVisitsPerQuarter());
        return applyPolicy(ctx, policy, null);
    }
}
