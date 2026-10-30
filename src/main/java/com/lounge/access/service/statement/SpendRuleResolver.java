package com.lounge.access.service.statement;

import com.lounge.access.config.LoungeProperties;
import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.service.engine.HdfcCreditTier;
import com.lounge.access.service.statement.SpendRule.WindowKind;
import com.lounge.access.util.InrFormat;
import com.lounge.access.util.LoungeConstants;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves the lounge rule for a bank/card/product from the same configuration the desk strategies use. */
@Component
public class SpendRuleResolver {

    private final LoungeProperties properties;

    public SpendRuleResolver(LoungeProperties properties) {
        this.properties = properties;
    }

    public Optional<SpendRule> resolve(Bank bank, CardType cardType, String productCode) {
        if (bank == Bank.AXIS) {
            LoungeProperties.Axis c = properties.getAxis();
            return Optional.of(new SpendRule("AXIS_BANK",
                    "Axis Bank: at least " + InrFormat.format(c.getMinimumSpend()) + " qualifying spend in the preceding "
                            + c.getLookbackMonths() + " calendar months (cash withdrawals, wallet reloads and fuel excluded); "
                            + c.getVisitsPerQuarter() + " visits per quarter. New cards are exempt until the end of "
                            + "issue month + " + c.getNewCardWaiverMonths() + " months.",
                    c.getMinimumSpend(), c.getVisitsPerQuarter(), WindowKind.ROLLING_MONTHS, c.getLookbackMonths(),
                    c.getNewCardWaiverMonths(), false));
        }
        if (bank == Bank.HDFC && cardType == CardType.DEBIT) {
            LoungeProperties.HdfcDebit c = properties.getHdfcDebit();
            return Optional.of(new SpendRule("HDFC_DEBIT",
                    "HDFC debit: at least " + InrFormat.format(c.getMinimumSpend()) + " qualifying spend in the previous "
                            + "calendar quarter and a 12-digit digital voucher generated before entry; "
                            + c.getVisitsPerQuarter() + " visits per quarter.",
                    c.getMinimumSpend(), c.getVisitsPerQuarter(), WindowKind.PREVIOUS_QUARTER, 3, -1, true));
        }
        if (bank == Bank.HDFC && cardType == CardType.CREDIT) {
            return HdfcCreditTier.fromProductCode(productCode).map(t -> new SpendRule("HDFC_CREDIT",
                    "HDFC " + t + ": "
                            + (t.minimumSpend().signum() == 0
                            ? "no spend requirement"
                            : "at least " + InrFormat.format(t.minimumSpend()) + " qualifying spend in the previous calendar quarter")
                            + "; " + (t.visitsPerQuarter() == LoungeConstants.UNLIMITED_VISITS
                            ? "unlimited visits" : t.visitsPerQuarter() + " visit(s) per quarter") + ".",
                    t.minimumSpend(), t.visitsPerQuarter(), WindowKind.PREVIOUS_QUARTER, 3, -1, false));
        }
        if (properties.getGeneric().getSupportedBanks().contains(bank)) {
            LoungeProperties.Generic c = properties.getGeneric();
            return Optional.of(new SpendRule("GENERIC_FALLBACK",
                    bank + " (standard programme): at least " + InrFormat.format(c.getMinimumSpend())
                            + " qualifying spend in the previous calendar quarter; " + c.getVisitsPerQuarter()
                            + " visits per quarter.",
                    c.getMinimumSpend(), c.getVisitsPerQuarter(), WindowKind.PREVIOUS_QUARTER, 3, -1, false));
        }
        return Optional.empty();
    }
}
