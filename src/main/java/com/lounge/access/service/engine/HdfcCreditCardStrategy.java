package com.lounge.access.service.engine;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.entity.Card;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import com.lounge.access.util.DateRangeUtil;
import org.springframework.stereotype.Component;

/**
 * HDFC credit / super-premium tiers (previous-quarter spend, visits per quarter):
 * Millennia 1,00,000 / 1; Regalia Gold 60,000 / 3; Infinia and Diners Black: no spend threshold, unlimited.
 */
@Component
public class HdfcCreditCardStrategy extends AbstractSpendLinkedStrategy {

    public HdfcCreditCardStrategy(SpendHistoryRepository spendHistoryRepository,
                                  LoungeVisitRepository loungeVisitRepository) {
        super(spendHistoryRepository, loungeVisitRepository);
    }

    @Override
    public String name() {
        return "HDFC_CREDIT";
    }

    @Override
    public boolean supports(Card card) {
        return card.getBank() == Bank.HDFC
                && card.getCardType() == CardType.CREDIT
                && HdfcCreditTier.fromProductCode(card.getProductCode()).isPresent();
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public EligibilityDecision evaluate(EligibilityContext ctx) {
        HdfcCreditTier tier = HdfcCreditTier.fromProductCode(ctx.card().getProductCode()).orElseThrow(
                () -> new IllegalStateException("Unsupported HDFC credit product " + ctx.card().getProductCode()));
        SpendPolicy policy = new SpendPolicy(
                tier.minimumSpend(),
                DateRangeUtil.previousQuarter(ctx.today()),
                false,
                null,
                tier.visitsPerQuarter());
        return applyPolicy(ctx, policy, null);
    }
}
