package com.lounge.access.service.engine;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.entity.Card;
import com.lounge.access.repository.LoungeVisitRepository;
import com.lounge.access.repository.SpendHistoryRepository;
import com.lounge.access.util.DateRangeUtil;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * HDFC credit / super-premium tiers (previous-quarter spend, visits per quarter):
 * Millennia 1,00,000 / 1; Regalia Gold 60,000 / 3; Infinia and Diners Black: no spend threshold, unlimited.
 */
@Component
public class HdfcCreditCardStrategy extends AbstractSpendLinkedStrategy {

    public enum Tier {
        MILLENNIA(new BigDecimal("100000"), 1),
        REGALIA_GOLD(new BigDecimal("60000"), 3),
        INFINIA(BigDecimal.ZERO, EligibilityDecision.UNLIMITED),
        DINERS_BLACK(BigDecimal.ZERO, EligibilityDecision.UNLIMITED);

        private final BigDecimal minimumSpend;
        private final int visitsPerQuarter;

        Tier(BigDecimal minimumSpend, int visitsPerQuarter) {
            this.minimumSpend = minimumSpend;
            this.visitsPerQuarter = visitsPerQuarter;
        }

        public BigDecimal minimumSpend() {
            return minimumSpend;
        }

        public int visitsPerQuarter() {
            return visitsPerQuarter;
        }

        public static Optional<Tier> fromProductCode(String productCode) {
            if (productCode == null) {
                return Optional.empty();
            }
            String normalized = productCode.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
            return Arrays.stream(values()).filter(t -> t.name().equals(normalized)).findFirst();
        }
    }

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
                && Tier.fromProductCode(card.getProductCode()).isPresent();
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public EligibilityDecision evaluate(EligibilityContext ctx) {
        Tier tier = Tier.fromProductCode(ctx.card().getProductCode()).orElseThrow(
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
